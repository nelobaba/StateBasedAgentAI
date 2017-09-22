
import java.io.*;
import java.util.*;
import java.util.stream.*;
import aima.core.agent.Action;
import aima.core.search.framework.problem.*;
import aima.core.search.nondeterministic.*;

public class AndOrAgent extends Thread {

    private Memory memory;
    private SendCommand krislet;
    private char side;
    private Path p = null;
    private Path successPath = null;
    private long time = System.currentTimeMillis();
    public boolean timeOver = false;
    public Map<StateType, State> sMap = new HashMap();


    public AndOrAgent(char side, Memory memory, SendCommand krislet) {
	this.side = side; this.memory = memory; this.krislet = krislet;
	for (StateType t : StateType.values()) sMap.put(t, new State(t,0));
    }

    @Override
    public void run() {
	int i = 0;
	while (i++ < 1000000) {
	    System.out.println("running AndOrSearch " + i);
	    p = new Path(sMap.get(StateType.BALL_NOT_IN_NET));
	    Plan plan = 
	        (new AndOrSearch()).search(new NondeterministicProblem(
		    p.s, stateActions(), nextState(), isGoal()));
	    if (successPath != null) log(successPath.toString());
	    else if (successPath == null) log(p.toString());
	}
    }

    private GoalTest isGoal() {
	return s -> StateType.BALL_IN_NET == ((State) s).type;
    }

    private ActionsFunction stateActions() {
	return s -> {
	    return new HashSet(Arrays.asList(Act.values()));
	};
    }

    private ResultsFunction nextState() {
	return (o, a) -> {
	    State s = (State) o;
	    ObjectInfo b = memory.getObject("ball");
	    ObjectInfo g = memory.getObject("goal "+ side);
	    ObjectInfo info = b==null? g: b;
	    double distance = info==null? 5.0: info.m_distance;
	    double direction = info==null? 20.1: info.m_direction;
	    if (a == Act.TURN) krislet.turn(direction);
	    else if (a == Act.DASH) krislet.dash(2000.0);
	    else if (a == Act.KICK) 
		krislet.kick(2000.0, g==null? direction: g.m_direction);
	    else if (a == Act.WAIT) memory.waitForNewInfo();
	    try {
	        Thread.sleep(2*SoccerParams.simulator_step);
	    } catch (Exception e){}
	    if (timeOver) krislet.bye();
	    return nextStates(s, a);
	};
    }

    private Set nextStates(State s, Action a) {
            Set s1 = new HashSet();
	    ObjectInfo b = memory.getObject("ball");
	    ObjectInfo g = memory.getObject("goal "+ side);
	    for (StateType t : StateType.values()) s1.add(sMap.get(t));
	    s1.remove(sMap.get(StateType.BALL_IN_NET));
	    if (b != null && g != null &&
		b.m_distance >= g.m_distance &&
		Math.abs(g.m_direction - b.m_direction)<=1) {
		s1.add(sMap.get(StateType.BALL_IN_NET));
		successPath = p;
	    }
	    for (Object ns : s1) 
		if (!s.p.contains((State)ns))
		    s.p.add(a, new Path((State)ns, s.p));
	    return s1;
    }

    private void log(String text) {
	BufferedWriter bw = null;
	try {
	    String file = "agent_" + time + ".log";
	    bw = new BufferedWriter(new FileWriter(file));
	    bw.write(text);
	    System.out.println("Wrote PATH to file " + file);
	} catch (IOException e) {
	    System.out.println(e.getMessage());
	    e.printStackTrace();
	} finally {
	    try { if (bw != null) bw.close(); } catch (IOException e) {}
	}
    }

    enum StateType { CHASE_BALL, KICK_BALL, BALL_NOT_IN_NET, BALL_IN_NET }

    class State {
	public int level = 0;
	public StateType type = StateType.BALL_NOT_IN_NET;
	private Path p = null;
	public State() {};
	public State(StateType t, int l) { type = t; level = l; }
	public boolean equals(State s) { 
	    return s.type==type && s.level==level; 
	}
	public String toString() { return ""+type; }
    }

    class Path {
	public State s;
	public Map<Action, List<Path>> aMap;
	private List<Path> all = new ArrayList();
	private int i = 0;
	private Path parent = null;
	public Path() { s = null; aMap = new HashMap(); }
	public Path(State state) { this(); s = state; s.p = this; }
	public Path(State state, Path p) { this(state); parent = p; }
	public void add(Action a, Path p) { 
	    if (!aMap.containsKey(a)) aMap.put(a, new ArrayList());
	    aMap.get(a).add(p); all.add(p); 
	}
	public boolean contains(State state) {
	    Path p = this;
	    while (p != null) {
		if (p.s.equals(state)) return true;
		p = p.parent;
	    }
	    return false;
	}
	public String toString() {
	    StringBuilder sb = new StringBuilder("{\"STATE_");
	    sb.append(s).append("->AND\":{");
	    int x = 0;
	    for (Action a : aMap.keySet())
		sb.append(x++==0? "": ", ").append("\"ACTION_").append(a).
	    	   append("->OR\": ").append(aMap.get(a));
	    return sb.append("}").append("}").toString();
	}
    }

    enum Act implements Action {
	TURN, DASH, KICK, WAIT;
	
	@Override
	public boolean isNoOp() { return false; }
    }
}
