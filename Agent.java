// Agent file for JSON formatter
import java.lang.Math;
import java.lang.Number;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.regex.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

class Agent 
{

    private String agentFile;
    private JSONObject jsonRoot = null;
    private String state = null;
    private Object value = null;
    private Memory envMem = null;
    private SendCommand envKris = null;
    private Character envSide = null;
    private Map<String, Object> symbols = new HashMap();
    private List<Boolean> isConditionPassed = new ArrayList();

    public boolean debug = false;

    //---------------------------------------------------------------------------
    // This constructor:
    // - stores the agent file name
    public Agent(String agentFile)
    {
	this.agentFile = agentFile;
    }


    public void load() throws IOException, ParseException
    {
	//Read agent from file
	jsonRoot = (JSONObject) (new JSONParser()).parse(
			    new FileReader(agentFile));
	if (jsonRoot == null) 
	    throw new IOException("No agent found in " + agentFile);
	System.out.println("MD: successfully loaded " + agentFile);
    }

    public void nextAction(char envSide, Memory envMem, SendCommand envKris) {
	this.envMem = envMem; 
	this.envKris = envKris;
	this.envSide = envSide;
	ObjectInfo ball = envMem.getObject("ball");
	JSONObject agent = (JSONObject) jsonRoot.get("agent");
	if (state != null) agent = (JSONObject) agent.get(state);
	reflect(agent);
	if (debug) System.out.println("MD: nextAction done");
	System.out.println("}");
    }

    private Object reflect(String json) {
	if (json.startsWith("obj_"))
	    return symbols.get(json.replace("obj_", ""));
	else if ("_side".equals(json))
	    return envSide;
	else if (json.startsWith("state_")) 
	    state = json;
	else if ("null".equals(json))
	    return null;
	Object obj = reflectVariableRef(json);
	if (obj != null) return obj;
	return json;
    }

    private Object reflect(Object obj) { //TODO: delete useless-looking method
	if (obj instanceof JSONObject)
	    return reflect((JSONObject) obj);
	else if (obj instanceof String)
	    return reflect((String) obj);
	return obj;
    }

    private Object reflect(JSONObject json) {
	Object obj = null;
	for (String key : keys(json)) {
	    if (key.startsWith("obj_")) {
		String varName = key.replace("obj_", "");
		symbols.put(varName, envMem.getObject(varName));
		obj = reflect(varName, json.get(key));
	    } else if (key.equals("agent")) {
		obj = reflect(json.get(key));
	    } else if (key.startsWith("state_") && state == null) {
		state = key;
		return reflect(json.get(key));
	    } else {
	        obj = reflect(key, json.get(key));
	    }
	}
	return obj;
    }

    private Object reflectVariableRef(String lhs) {
	if (symbols.containsKey(lhs))
	    return symbols.get(lhs);
	for (String varName : symbols.keySet()) {
	    if (lhs.startsWith(varName + ".")) {
	        String member = lhs.replace(varName + ".", "");
		switch (member) {
		    case "direction": 
		        return ((ObjectInfo)symbols.get(varName)).m_direction;
		    case "distance":
			return ((ObjectInfo)symbols.get(varName)).m_distance; 
		    default:
		}
	    }
	}
	return null;
    }

    private Object reflect(String lhs, Object rhs) {
	if (debug) 
	    System.out.println("MD: reflect1.lhs=" + lhs + ", rhs=" + rhs);
	Object obj = reflectVariableRef(lhs);
	if (obj != null && rhs == null) return obj;

        switch (lhs) {
  	    case "waitForNewInfo": envMem.waitForNewInfo(); return obj;
	    case "turn": envKris.turn(toLong(reflect(rhs))); return obj;
	    case "dash": envKris.dash(toLong(reflect(rhs))); return obj;
	    case "kick": 
		envKris.kick(toLong(reflect(((JSONArray) rhs).get(0))),
		     	toLong(reflect(((JSONArray) rhs).get(1))));
		return obj;
  	    case "actions": 
		for (int i = 0; i < ((JSONArray) rhs).size(); i++) 
		    obj = reflect(((JSONArray) rhs).get(i));
		return obj;
	    default: break;
	}
	int i = isConditionPassed.size();
	isConditionPassed.add(false);
	for (String key : keys((JSONObject) rhs)) {
	    if (!isConditionPassed.get(i)) {
	        obj = reflect(lhs, key, ((JSONObject)rhs).get(key));
	        if (Pattern.matches("([=><!][=]|[><]|else)", key))
	   	    isConditionPassed.set(i-1>=0? i-1: i, isConditionPassed.get(i));
	    }
	}
	isConditionPassed.remove(i);
	return obj;
    }

    private Object reflect(String lhs, String op, Object rhs) {
	Object obj = null;
	if (debug) System.out.println("MD: isCond="+isConditionPassed);
	if (Pattern.matches("([=><!][=]|[><]|else)", op))
	    return reflectConditional(lhs, op, rhs);
	switch (op) {
	    case "=": symbols.put(lhs, reflect(rhs)); return obj;
	    case "*": return toNum(reflect(lhs)) * toNum(reflect(rhs)); 
    	    case "/": return toNum(reflect(lhs)) / toNum(reflect(rhs));
    	    case "+": return toNum(reflect(lhs)) + toNum(reflect(rhs));
    	    case "-": return toNum(reflect(lhs)) - toNum(reflect(rhs));
	}
	if (debug)
	    System.out.println("MD: reflect2.lhs=" + lhs + ", op=" + op + ", rhs=" + rhs);
	return reflect(op, rhs);
    }

    private Object reflectConditional(String lhs, String op, Object rhsObj) {
	if (debug) 
	    System.out.println("MD: reflectCond1.lhs=" + lhs + ", op=" + op);
	Object obj = null;
	Object lVal = reflectVariableRef(lhs);
	JSONObject rhs = (JSONObject) rhsObj;
	for (String key : keys(rhs)) {
	    Object val = op=="else"? null: toObject(lVal, key);
	    Object rval = rhs.get(key);
	    if (debug)
		System.out.println("MD: reflectCond2.lhs="+lVal+", rhs="+val+", key="+key+", rval="+rval +", rhsObj="+rhs);
	    switch (op) {
		case "==": if (equal(lVal, val)) return pass(rval); break;
		case ">": if (toNum(lVal)>toNum(val)) return pass(rval); break;
		case "<": if (toNum(lVal)<toNum(val)) return pass(rval); break;
		case ">=": if (toNum(lVal) >= toNum(val)) 
				   return pass(rval); break;
		case "<=": if (toNum(lVal) <= toNum(val)) 
				   return pass(rval); break;
		case "!=": if (!equal(toNum(lVal), toNum(val)))
				   return pass(rval); break;
		case "else": return pass(rhs);
	    }
	}
	return obj;
    }

    private Object pass(Object obj) {
	Object result = reflect(obj);
	isConditionPassed.set(isConditionPassed.size()-1, true);
	if (debug) System.out.println("pass.isCond=" + isConditionPassed);
	return result;
    }

    private List<String> keys(JSONObject obj) {
	List<String> keyList = new ArrayList<String>();
	if (obj == null) return keyList;
	for (Object key : obj.keySet()) 
	    keyList.add((String) key);
	Collections.sort(keyList);
	if (keyList.contains("else")) { // Ensure "else" comes last
	    keyList.remove("else");
	    keyList.add("else");
	}
	if (debug) System.out.println("MD: keys=" + keyList);
	return keyList;
    }

    private Object toObject(Object typeObj, String val) {
	if ("null".equals(val)) return null;
	if (typeObj instanceof Integer) return Integer.valueOf(val);
	else if (typeObj instanceof Long) return Long.valueOf(val);
	else if (typeObj instanceof Float) return Float.valueOf(val);
	else if (typeObj instanceof Double) return Double.valueOf(val);
	return null;
    }

    private boolean equal(Object a, Object b) {
	return a==null && b==null || a.equals(b);
    }

    private Double toNum(Object val) {
	return val==null? null: Double.valueOf(String.valueOf(val));
    }

    private Long toLong(Object val) {
	if (val instanceof Float) return ((Float) val).longValue();
	if (val instanceof Double) return ((Double) val).longValue();
	return val==null? null: Long.valueOf(String.valueOf(val));
    }
    
}
