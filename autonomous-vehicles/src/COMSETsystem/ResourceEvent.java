package COMSETsystem;

import DataParsing.MapWithData;
import DataParsing.Resource;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author TijanaKlimovic
 */
/**
 * The ResourceEvent class represents the moment a resource becomes available or expired in
 * the simulation. Therefore there are two cases in which a ResourceEvent object is triggered:
 * 
 * 1. When the resource is introduced to the system and becomes available. In this case, the 
 * resource is assigned (if there are agents available and within reach) to an agent.
 * 2. When the resource gets expired.
 */
public class ResourceEvent extends Event {

	// Constants representing two causes for which the ResourceEvent can be triggered.
	public final static int BECOME_AVAILABLE = 0;
	public final static int EXPIRED = 1;

	// The location at which the resource is introduced.
	public final LocationOnRoad pickupLoc;
	// The destination of the resource.
	public final LocationOnRoad dropoffLoc;

	// The time at which the resource is introduced
	final long availableTime; 

	// The time at which the resource will be expired
	final long expirationTime;

	// The cause of the AgentEvent to be triggered, either BECOME_AVAILABLE or EXPIRED
	int eventCause;

	// The shortest travel time from pickupLoc to dropoffLoc
	public long tripTime;

	/**
	 * Constructor for class ResourceEvent.
	 *
	 * @param availableTime time when this agent is introduced to the system.
	 * @param pickupLoc this resource's location when it becomes available.
	 * @param dropoffLoc this resource's destination location.
	 * @param simulator the simulator object.
	 */
	public ResourceEvent(LocationOnRoad pickupLoc, LocationOnRoad dropoffLoc, long availableTime, Simulator simulator) {
		super(availableTime, simulator);
		this.pickupLoc = pickupLoc;
		this.dropoffLoc = dropoffLoc;
		this.availableTime = availableTime;
		this.eventCause = BECOME_AVAILABLE;
		this.expirationTime = availableTime + simulator.ResourceMaximumLifeTime;
		this.tripTime = simulator.map.travelTimeBetween(pickupLoc, dropoffLoc);
	}

	/**
	 * Whenever a resource arrives/becomes available an event corresponding to
	 * it gets triggered. When it triggers it checks for all the active agents
	 * which agent can get to the resource the fastest. The closest agent is
	 * saved in the variable bestAgent. If there are no active agents or no
	 * agent can get in time to the resource, the current resource gets added to
	 * waitingResources such that once an agent gets available it will check if
	 * it can get to the resource in time. Furthermore, calculate the score of
	 * this assignment according to the scoring rules. Also remove the assigned
	 * agent from the PriorityQueue and from activeAgents.
	 */
	@Override
	Event trigger() throws Exception {

		Logger.getLogger(this.getClass().getName()).log(Level.INFO, "******** ResourceEvent id = "+Long.toString(id) + " triggered at time " + time, this);
		Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Loc = " + this.pickupLoc + "," + this.dropoffLoc, this);

		if (simulator.map == null) {
			System.out.println("map is null in resource");
		}
		if (pickupLoc == null) {
			System.out.println("intersection is null");
		}
		if (eventCause == BECOME_AVAILABLE) {
			becomeAvailableHandler();
			return null;
		} else {
			expireHandler();
			return null;
		}

	}

	/*
	 * Handler of a BECOME_AVAILABLE event
	 */
	// Add the resource to the pool matrix
	public void becomeAvailableHandler() {
		simulator.resourceMatrix.add(this);
		++simulator.totalResources;

	}

	/*
	 * Handler of an EXPIRED event.
	 */
	public void expireHandler() {
		simulator.expiredResources ++;
		simulator.totalResourceWaitTime += simulator.ResourceMaximumLifeTime;
		simulator.waitingResources.remove(this);
		Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Expired.", this);

	}
}
