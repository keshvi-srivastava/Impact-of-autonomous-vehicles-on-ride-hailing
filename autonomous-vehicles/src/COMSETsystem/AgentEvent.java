package COMSETsystem;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author TijanaKlimovic
 */
/**
 * The AgentEvent class represents a moment an agent is going to perform an
 * action in the simmulation, such as becoming empty and picking up a
 * resource, or driving to some other Intersection. 
 *
 * An AgentEvent is triggered in either of the following cases:
 *
 * 1. The agent reaches an intersection.   
 * 2. The agent drops off a resource.
 *
 * In the case that the agent reaches an intersection, the AgentEvent invokes agent.nextIntersection()
 * to let the agent determine which of the neighboring intersections to go to. The AgentEvent is triggered
 * again when the agent reaches the next intersection, and so on. This is how the agent's search route
 * is executed. The searching ends when the agent is assigned to a resource, in which case the AgentEvent
 * is set to be triggered at the time when the agent drops off the resource to its destination.
 *
 * In the case that the agent drops off a resource, the AgentEvent checks if there are waiting resources. If so,
 * the AgentEvent assigns the agent to the closest waiting resource if the travel time from the agent's current location 
 * to the resource is smaller than the resource's remaining life time. Otherwise the AgentEvent moves the agent to 
 * the end intersection of the current road. 
 */
public class AgentEvent extends Event {

	// Constants representing two causes for which the AgentEvent can be triggered.
	public final static int INTERSECTION_REACHED = 0;
	public final static int DROPPING_OFF = 1;

	// The location at which the event is triggered.
	LocationOnRoad loc;

	// The Agent object that the event pertains to.
	public BaseAgent agent;

	// The cause of the AgentEvent to be triggered, either INTERSECTION_REACHED or DROPPING_OFF.
	public int eventCause;

	/*
	 * The time at which the agent started to search for a resource. This is also the
	 * time at which the agent drops off a resource.
	 */
	long startSearchTime;

	/**
	 * Constructor for class AgentEvent.
	 *
//	 * @param time when this agent starts search.
	 * @param loc this agent's location when it becomes empty.
	 */
	public AgentEvent(LocationOnRoad loc, long startedSearch, Simulator simulator) {
		super(startedSearch, simulator);
		this.loc = loc;
		this.startSearchTime = startedSearch;
		this.eventCause = DROPPING_OFF; // The introduction of an agent is considered a drop-off event.
		simulator.emptyAgents.add(this);
		try {
			Constructor<? extends BaseAgent> cons = simulator.agentClass.getConstructor(Long.TYPE, CityMap.class);
			agent = cons.newInstance(id, simulator.mapForAgents);
		} catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Initializes the agent corresponding to this AgentEvent.
	 */
	public void initAgent() {
		try {
			Constructor<? extends BaseAgent> cons = simulator.agentClass.getConstructor(Long.TYPE, CityMap.class);
			agent = cons.newInstance(id, simulator.mapForAgents);
		} catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
			e.printStackTrace();
		}
	}

	@Override
	Event trigger() throws Exception {
		Logger.getLogger(this.getClass().getName()).log(Level.INFO, "******** AgentEvent id = " + id+ " triggered at time " + time, this);
		Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Loc = " + loc, this);
		//System.out.println("Agent Event");
		Event e;
		if (eventCause == DROPPING_OFF) {
			e = dropoffHandler();
		} else {
			e = intersectionReachedHandler();
		}
		// add this event back on the event queue
		return e;
	}

	/*
	 * The handler of an INTERSECTION_REACHED event.
	 */
	Event intersectionReachedHandler() throws Exception{
		assert loc.travelTimeFromStartIntersection == loc.road.travelTime : "Agent not at an intersection.";

		// Ask the agent to choose the next intersection to move to.
		LocationOnRoad locAgentCopy = simulator.agentCopy(loc);
		Intersection nextIntersection = agent.nextIntersection(locAgentCopy, time);
		if (nextIntersection == null) {
			throw new Exception("agent.move() did not return a next location");
		}

		if (!loc.road.to.isAdjacent(nextIntersection)) {
			throw new Exception("move not made to an adjacent location");
		}

		// set location and time of the next trigger
		Road nextRoad = loc.road.to.roadTo(nextIntersection);
		LocationOnRoad nextLocation = new LocationOnRoad(nextRoad, nextRoad.travelTime);
		setEvent(time + nextRoad.travelTime, nextLocation, INTERSECTION_REACHED);

		Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Move to " + nextRoad.to, this);
		Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Next trigger time = " + time, this);
		return this;
	}

	/*
	 * The handler of a DROPPING_OFF event.
	 */
	Event dropoffHandler() {
		startSearchTime = time;

		Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Dropoff at " + loc, this);


		LocationOnRoad locAgentCopy = simulator.agentCopy(loc);
		agent.planSearchRoute(locAgentCopy, time);
		// no resources have been assigned to the agent 
		// so if the agent was not empty, make it empty for other resources
		if (!simulator.emptyAgents.contains(this)) {
			// "Label" the agent as empty.
			simulator.emptyAgents.add(this);
		}
		// move to the end intersection of the current road
		long nextEventTime = time + loc.road.travelTime - loc.travelTimeFromStartIntersection;
		LocationOnRoad nextLoc = new LocationOnRoad(loc.road, loc.road.travelTime);
		setEvent(nextEventTime, nextLoc, INTERSECTION_REACHED);

		return this;
	}

	public void assignedTo(LocationOnRoad currentLocation, long currentTime, long resourceId, LocationOnRoad resourcePickupLocation, LocationOnRoad resourceDropoffLocation) {
		LocationOnRoad currentLocationAgentCopy = simulator.agentCopy(currentLocation);
		LocationOnRoad resourcePickupLocationAgentCopy = simulator.agentCopy(resourcePickupLocation);
		LocationOnRoad resourceDropoffLocationAgentCopy = simulator.agentCopy(resourceDropoffLocation);
		agent.assignedTo(currentLocationAgentCopy, currentTime, resourceId, resourcePickupLocationAgentCopy, resourceDropoffLocationAgentCopy);
	}

	public void setEvent(long time, LocationOnRoad loc, int eventCause) {
		this.time = time;
		this.loc = loc;
		this.eventCause = eventCause;
	}
}
