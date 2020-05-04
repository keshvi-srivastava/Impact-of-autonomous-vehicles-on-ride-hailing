package COMSETsystem;

import MapCreation.*;

import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import UserExamples.Hungarian;
import me.tongfei.progressbar.*;



import DataParsing.*;

import static java.lang.Math.min;

/**
 * The Simulator class defines the major steps of the simulation. It is
 * responsible for loading the map, creating the necessary number of agents,
 * creating a respective AgentEvent for each of them such that they are added
 * to the events PriorityQueue. Furthermore it is also responsible for dealing
 * with the arrival of resources, map matching them to the map, and assigning
 * them to agents. This produces the score according to the scoring rules.
 * <p>
 * The time is simulated by the events having a variable time, this time
 * corresponds to when something will be empty and thus needs some
 * interaction (triggering). There's an event corresponding to every existent
 * Agent and for every resource that hasn't arrived yet. All of this events are
 * in a PriorityQueue called events which is ordered by their time in an
 * increasing way.
 */
public class Simulator {

	//list of hubs
	List<Intersection> hubs;

	//List of hubs as LocationOnRoad
	List<LocationOnRoad> hubsLocationOnRoad = new ArrayList<>();

	// The map that everything will happen on.
	protected CityMap map;

	// A deep copy of map to be passed to agents.
	// This is a way to make map unmodifiable.
	protected CityMap mapForAgents;

	// The event queue.
	protected PriorityQueue<Event> events = new PriorityQueue<>();

	// The set of empty agents.
	protected TreeSet<AgentEvent> emptyAgents = new TreeSet<>(new AgentEventComparator());

	// The set of resources that with no agent assigned to it yet.
	protected TreeSet<ResourceEvent> waitingResources = new TreeSet<>(new ResourceEventComparator());

	// The maximum life time of a resource in seconds. This is a parameter of the simulator.
	public long ResourceMaximumLifeTime;

	// Full path to an OSM JSON map file
	protected String mapJSONFile;


	// Full path to a TLC New York Yellow trip record file
	protected String resourceFile = null;

	// Full path to a KML defining the bounding polygon to crop the map
	protected String boundingPolygonKMLFile;

	// The simulation end time is the expiration time of the last resource.
	protected long simulationEndTime;

	// Total trip time of all resources to which agents have been assigned.
	protected long totalResourceTripTime = 0;

	// Total wait time of all resources. The wait time of a resource is the amount of time
	// since the resource is introduced to the system until it is picked up by an agent.
	protected long totalResourceWaitTime = 0;

	// Total search time of all agents. The search time of an agent for a research is the amount of time
	// since the agent is labeled as empty, i.e., added to emptyAgents, until it picks up a resource.
	protected long totalAgentSearchTime = 0;

	// Total cruise time of all agents. The cruise time of an agent for a research is the amount of time
	// since the agent is labeled as empty until it is assigned to a resource.
	protected long totalAgentCruiseTime = 0;

	// Total approach time of all agents. The approach time of an agent for a research is the amount of time
	// since the agent is assigned to a resource until agent reaches the resource.
	protected long totalAgentApproachTime = 0;

	// The number of expired resources.
	protected long expiredResources = 0;

	// The number of resources that have been introduced to the system.
	protected long totalResources = 0;

	// The number of agents that are deployed (at the beginning of the simulation).
	protected long totalAgents;

	// The number of assignments that have been made.
	protected long totalAssignments = 0;

	// A list of all the agents in the system. Not really used in COMSET, but maintained for
	// a user's debugging purposes.
	ArrayList<BaseAgent> agents;

	// A class that extends BaseAgent and implements a search routing strategy
	protected final Class<? extends BaseAgent> agentClass;

	public ArrayList<ArrayList<Double>> costMatrix = new ArrayList<ArrayList<Double>>();
	public ArrayList<AgentEvent> agentMatrix = new ArrayList<AgentEvent>();
	public ArrayList<ResourceEvent> resourceMatrix = new ArrayList<ResourceEvent>();

	public ArrayList<ResourceEvent> tempResourceMatrix = new ArrayList<ResourceEvent>();

	public long initialPoolTime;
	public long lastResourceTime;
	public long endPoolTime;
	public long triggerTime;
	public int count=0;

	public ArrayList<Double> poolBenefits = new ArrayList<Double>();
	public double poolBenefitsSum = 0;

	public double perPoolTime = 0;
	public int pool =0;
	/**
	 * Constructor of the class Main. This is made such that the type of
	 * agent/resourceAnalyzer used is not hardcoded and the users can choose
	 * whichever they wants.
	 *
	 * @param agentClass the agent class that is going to be used in this
	 * simulation.
	 */
	public Simulator(Class<? extends BaseAgent> agentClass) {
		this.agentClass = agentClass;
	}

	/**
	 * Configure the simulation system including:
	 *
	 * 1. Create a map from the map file and the bounding polygon KML file.
	 * 2. Load the resource data set and map match.
	 * 3. Create the event queue.
	 *
	 * See Main.java for detailed description of the parameters.
	 *
	 * @param mapJSONFile The map file
	 * @param resourceFile The dataset file
	 * @param totalAgents The total number of agents to deploy
	 * @param boundingPolygonKMLFile The KML file defining a bounding polygon of the simulated area
	 * @param maximumLifeTime The maximum life time of a resource
	 * @param agentPlacementRandomSeed The seed for the random number of generator when placing the agents
	 * @param speedReduction The speed reduction to accommodate traffic jams and turn delays
	 */
	public void configure(String mapJSONFile, String resourceFile, Long totalAgents, String boundingPolygonKMLFile, Long maximumLifeTime, long agentPlacementRandomSeed, double speedReduction) {

		this.mapJSONFile = mapJSONFile;

		this.totalAgents = totalAgents;

		this.boundingPolygonKMLFile = boundingPolygonKMLFile;

		this.ResourceMaximumLifeTime = maximumLifeTime;

		this.resourceFile = resourceFile;

		MapCreator creator = new MapCreator(this.mapJSONFile, this.boundingPolygonKMLFile, speedReduction);
		System.out.println("Creating the map...");

		creator.createMap();

		this.hubs = creator.hubs;

		// Output the map
		map = creator.outputCityMap();

		// Pre-compute shortest travel times between all pairs of intersections.
		System.out.println("Pre-computing all pair travel times...");
		map.calcTravelTimes();

		// Make a map copy for agents to use so that an agent cannot modify the map used by
		// the simulator
		mapForAgents = map.makeCopy();

		MapWithData mapWD = new MapWithData(map, this.resourceFile, agentPlacementRandomSeed);

		//Use mapWD to get list of hubs as LocationOnRoad
		for(int i=0;i<hubs.size();i++)
			hubsLocationOnRoad.add(mapWD.mapMatch(hubs.get(i).longitude,hubs.get(i).latitude));

		// map match resources
		System.out.println("Loading and map-matching resources...");
		long latestResourceTime = mapWD.createMapWithData(this);

		// The simulation end time is the expiration time of the last resource.
		this.simulationEndTime = latestResourceTime;

		// Deploy agents at random locations of the map.
		System.out.println("Randomly placing " + this.totalAgents + " agents on the map...");
		agents = mapWD.placeAgentsRandomly(this);

		// Initialize the event queue.
		events = mapWD.getEvents();
	}

	/**
	 * This method corresponds to running the simulation. An object of ScoreInfo
	 * is created in order to keep track of performance in the current
	 * simulation. Go through every event until the simulation is over.
	 *
	 * @throws Exception since triggering events may create an Exception
	 */
	public void run() throws Exception {
		System.out.println("Running the simulation...");

		initialPoolTime = initialPoolTime + TimeUnit.SECONDS.toSeconds(30);  //8:02
		endPoolTime = initialPoolTime + TimeUnit.SECONDS.toSeconds(30);  //8:04
		ScoreInfo score = new ScoreInfo();


		System.out.println("Initial pool time: " + initialPoolTime);

		System.out.println("Start pool time: " +  initialPoolTime);
		System.out.println("End pool time: " + endPoolTime);
		if (map == null) {
			System.out.println("map is null at beginning of run");
		}



		try (ProgressBar pb = new ProgressBar("Progress:", 100, ProgressBarStyle.ASCII)) {
			long beginTime = events.peek().time;
			while (events.peek().time <= simulationEndTime) {
				Event toTrigger = events.poll();
				pb.stepTo((long)(((float)(toTrigger.time - beginTime)) / (simulationEndTime - beginTime) * 100.0));

				triggerTime = toTrigger.time;

				if (toTrigger.getClass() == ResourceEvent.class &&
						(toTrigger.time>=initialPoolTime)){

					if (resourceMatrix.isEmpty()){
						continue;
					}
					long startPoolTime = System.nanoTime();
					getCostMatrix();
					runAlgo(triggerTime);
					costMatrix.clear();
					agentMatrix.clear();
					resourceMatrix.clear();
					initialPoolTime = initialPoolTime + TimeUnit.SECONDS.toSeconds(30); // 8:04
					endPoolTime = initialPoolTime + TimeUnit.SECONDS.toSeconds(30); // 8:06
					long endPoolTime = System.nanoTime();
					perPoolTime = perPoolTime + (endPoolTime - startPoolTime);
				}
				Event e = toTrigger.trigger();
				if (e != null) {
					events.add(e);
				}
			}
			if(!resourceMatrix.isEmpty()){
				System.out.println("In the last loop");
				getCostMatrix();
				runAlgo(triggerTime);
			}
			else{
				for (ResourceEvent re : waitingResources){
					expiredResources ++;
					totalResourceWaitTime += ResourceMaximumLifeTime;
				}
			}
			for(Double benefit : poolBenefits)
				poolBenefitsSum += benefit;

		} catch (Exception e) {
			e.printStackTrace();
		}

		System.out.println("Simulation finished.");

		score.end();
	}

	public void getCostMatrix(){

		System.out.println("# of resources: " + resourceMatrix.size());
		System.out.println("# of agents: " + emptyAgents.size());


		for (ResourceEvent resource: resourceMatrix){

			ArrayList<Double> benefitList = new ArrayList<>();
			double travelDist = MapWithData.distance(resource.pickupLoc.toLatLon()[0], resource.pickupLoc.toLatLon()[1],
					resource.dropoffLoc.toLatLon()[0], resource.dropoffLoc.toLatLon()[1]);
			for (AgentEvent agent : emptyAgents) {
				double dist = MapWithData.distance(agent.loc.toLatLon()[0], agent.loc.toLatLon()[1],
						resource.pickupLoc.toLatLon()[0], resource.pickupLoc.toLatLon()[1]);

				double benefit = travelDist / (travelDist + dist);

				benefitList.add(benefit);
			}
			costMatrix.add(benefitList);
		}

		for (AgentEvent agent: emptyAgents){
			agentMatrix.add(agent);
		}

	}
	public void runAlgo(long time){


		double[][] temp = costMatrix.stream().map(u->u.stream().mapToDouble(i->i).toArray()).toArray(double[][]::new);

		double[][] copy1 = Arrays.stream(temp).map(double[]::clone).toArray(double[][]::new);

		int[][] assignment = Hungarian.hgAlgorithm(copy1, "max");

		double sum = 0;

		if (assignment.length > 0) {
			long earliest = Long.MAX_VALUE;
			LocationOnRoad bestAgentLocationOnRoad = null;
			ResourceEvent currentResource;
			AgentEvent bestAgent;
			int agentIndex;
			int resourceIndex;

			for (int i = 0; i < assignment.length; i++) {

				if (temp.length > temp[0].length){
					currentResource = resourceMatrix.get(assignment[i][1]);
					bestAgent = agentMatrix.get(assignment[i][0]);
					resourceIndex = assignment[i][1];
					agentIndex = assignment[i][0];
				}
				else{
					currentResource = resourceMatrix.get(assignment[i][0]);
					bestAgent = agentMatrix.get(assignment[i][1]);
					resourceIndex = assignment[i][0];
					agentIndex = assignment[i][1];
				}

				if (i< min(costMatrix.get(0).size(), costMatrix.size())){

					sum = sum + costMatrix.get(resourceIndex).get(agentIndex);
					long travelTimeToEndIntersection = bestAgent.time - time;

					long travelTimeFromStartIntersection = bestAgent.loc.road.travelTime - travelTimeToEndIntersection;
					LocationOnRoad agentLocationOnRoad = new LocationOnRoad(bestAgent.loc.road, travelTimeFromStartIntersection);
					long travelTime = map.travelTimeBetween(agentLocationOnRoad, currentResource.pickupLoc);
					long arriveTime = travelTime + time;
					if (arriveTime < earliest) {
						earliest = arriveTime;
						bestAgentLocationOnRoad = agentLocationOnRoad;
					}

					long cruiseTime = time - bestAgent.startSearchTime;
					long approachTime = earliest - time;
					long searchTime = cruiseTime + approachTime;
					long waitTime = earliest - currentResource.availableTime;

					totalAgentCruiseTime += cruiseTime;
					totalAgentApproachTime += approachTime;
					totalAgentSearchTime += searchTime;
					totalResourceWaitTime += waitTime;
					totalResourceTripTime += currentResource.tripTime;
					totalAssignments++;

					emptyAgents.remove(bestAgent);
					events.remove(bestAgent);

					waitingResources.remove(currentResource);
					events.remove(currentResource);

					TreeMap<Long,LocationOnRoad> dropOffHubTime = getDropOffHubTime(currentResource.dropoffLoc,hubsLocationOnRoad);
					LocationOnRoad nearestHub = dropOffHubTime.firstEntry().getValue();

					bestAgent.assignedTo(bestAgentLocationOnRoad,
							time, currentResource.id, currentResource.pickupLoc, currentResource.dropoffLoc);

					if(dropOffHubTime.firstKey()<60){
						bestAgent.setEvent(earliest + currentResource.tripTime+dropOffHubTime.firstKey(),
								nearestHub, AgentEvent.DROPPING_OFF);
					}else
						bestAgent.setEvent(earliest + currentResource.tripTime,
								currentResource.dropoffLoc, AgentEvent.DROPPING_OFF);

					events.add(bestAgent);
				}
				else if (costMatrix.get(0).size() > costMatrix.size()) {
					if (!emptyAgents.contains(bestAgent)){
						emptyAgents.add(bestAgent);
					}
				}
				else {
					count++;
					List<ResourceEvent> tempMatrix = resourceMatrix.subList(i, resourceMatrix.size());

					for (ResourceEvent resourceEvent : tempMatrix){
						count++;
						waitingResources.remove(resourceEvent);
						events.remove(resourceEvent);
						resourceEvent.time += ResourceMaximumLifeTime;
						resourceEvent.eventCause = ResourceEvent.EXPIRED; //EXPIRED
						waitingResources.add(resourceEvent);
						events.add(resourceEvent);
					}
					break;

				}
			}
			poolBenefits.add(sum);
			System.out.println(sum);
		} else {
			System.out.println("no assignment found!");
		}



	}
	/**
	 * This class is used to give a performance report and the score. It prints
	 * the total running time of the simulation, the used memory and the score.
	 * It uses Runtime which allows the application to interface with the
	 * environment in which the application is running.
	 */

	public double[][] transposeMatrix(double[][] a) {

		if (a.length > a[0].length){
			for(int i=0 ; i<a[0].length; i++) {
				for(int j=0 ; j<i ; j++) {
					double temp = a[i][j];
					a[i][j] = a[j][i];
					a[j][i] = temp;
				}
			}
		}
		else{
			for(int i=0 ; i<a.length; i++) {
				for(int j=0 ; j<i ; j++) {
					double temp = a[i][j];
					a[i][j] = a[j][i];
					a[j][i] = temp;
				}
			}
		}

		return a;
	}
	class ScoreInfo {

		Runtime runtime = Runtime.getRuntime();
		NumberFormat format = NumberFormat.getInstance();
		StringBuilder sb = new StringBuilder();

		long startTime;
		long allocatedMemory;

		/**
		 * Constructor for ScoreInfo class. Runs beginning, this method
		 * initializes all the necessary things.
		 */
		ScoreInfo() {
			startTime = System.nanoTime();
			// Suppress memory allocation information display
			// beginning();
		}

		/**
		 * Initializes and gets the max memory, allocated memory and free
		 * memory. All of these are added to the Performance Report which is
		 * saved in the StringBuilder. Furthermore also takes the time, such
		 * that later on we can compare to the time when the simulation is over.
		 * The allocated memory is also used to compare to the allocated memory
		 * by the end of the simulation.
		 */
		void beginning() {
			// Getting the memory used
			long maxMemory = runtime.maxMemory();
			allocatedMemory = runtime.totalMemory();
			long freeMemory = runtime.freeMemory();

			// probably unnecessary
			sb.append("Performance Report: " + "\n");
			sb.append("free memory: " + format.format(freeMemory / 1024) + "\n");
			sb.append("allocated memory: " + format.format(allocatedMemory / 1024)
					+ "\n");
			sb.append("max memory: " + format.format(maxMemory / 1024) + "\n");

			// still looking into this one "freeMemory + (maxMemory -
			// allocatedMemory)"
			sb.append("total free memory: "
					+ format.format(
					(freeMemory + (maxMemory - allocatedMemory)) / 1024)
					+ "\n");

			System.out.print(sb.toString());
		}

		/**
		 * Calculate the time the simulation took by taking the time right now
		 * and comparing to the time when the simulation started. Add the total
		 * time to the report and the score as well. Furthermore, calculate the
		 * allocated memory by the participant's implementation by comparing the
		 * previous allocated memory with the current allocated memory. Print
		 * the Performance Report.
		 */
		void end() {
			// Empty the string builder
			sb.setLength(0);

			long endTime = System.nanoTime();
			long totalTime = (endTime - startTime) / 1000000000;

			System.out.println("\nrunning time: " + totalTime);

			System.out.println("\n***Simulation environment***");
			System.out.println("JSON map file: " + mapJSONFile);
			System.out.println("Resource dataset file: " + resourceFile);
			System.out.println("Bounding polygon KML file: " + boundingPolygonKMLFile);
			System.out.println("Number of agents: " + totalAgents);
			System.out.println("Number of resources: " + totalResources);
			System.out.println("Resource Maximum Life Time: " + ResourceMaximumLifeTime + " seconds");

			System.out.println("\n***Statistics***");

			if (totalResources != 0) {
				// Collect the "search" time for the agents that are empty at the end of the simulation.
				// These agents are in search status and therefore the amount of time they spend on
				// searching until the end of the simulation should be counted toward the total search time.
				long totalRemainTime = 0;
				for (AgentEvent ae: emptyAgents) {
					totalRemainTime += (simulationEndTime - ae.startSearchTime);
				}

				sb.append("average agent search time: " + Math.floorDiv(totalAgentSearchTime + totalRemainTime, (totalAssignments + emptyAgents.size())) + " seconds \n");
				sb.append("average resource wait time: " + Math.floorDiv(totalResourceWaitTime, totalResources) + " seconds \n");
				sb.append("# of resources expired: " + expiredResources + "\n");
				sb.append("total number of resources: " + totalResources + "\n");
				sb.append("resource expiration percentage: " + (((double) expiredResources/ (double) totalResources)*100) + "%\n");
				sb.append("\n");

				//Changes made to pool division term!!!
				sb.append("avg benefit per taxi: " + (poolBenefitsSum/totalAgents) + "\n");
				sb.append("average agent cruise time: " + Math.floorDiv(totalAgentCruiseTime, totalAssignments) + " seconds \n");
				sb.append("average agent approach time: " + Math.floorDiv(totalAgentApproachTime, totalAssignments) + " seconds \n");
				sb.append("average resource trip time: " + Math.floorDiv(totalResourceTripTime, totalAssignments) + " seconds \n");
				sb.append("total number of assignments: " + totalAssignments + "\n");

				sb.append("total pool time " + perPoolTime + "\n");
				sb.append("avg pool time " + perPoolTime/poolBenefits.size() + "\n");

				sb.append("total number of pools: " + poolBenefits.size() + "\n");
			} else {
				sb.append("No resources.\n");
			}

			System.out.print(sb.toString());
		}
	}

	private TreeMap<Long, LocationOnRoad> getDropOffHubTime(LocationOnRoad dropoffLoc, List<LocationOnRoad> hubsLocationOnRoad) {
		TreeMap<Long,LocationOnRoad> dropOffHubTime = new TreeMap<>();
		for(int i=0;i<hubs.size();i++)
			dropOffHubTime.put(getMap().travelTimeBetween(dropoffLoc,hubsLocationOnRoad.get(i)),hubsLocationOnRoad.get(i));

		return dropOffHubTime;
	}

	/**
	 * Compares agent events
	 */
	class AgentEventComparator implements Comparator<AgentEvent> {

		/**
		 * Checks if two agentEvents are the same by checking their ids.
		 *
		 * @param a1 The first agent event
		 * @param a2 The second agent event
		 * @return returns 0 if the two agent events are the same, 1 if the id of
		 * the first agent event is bigger than the id of the second agent event,
		 * -1 otherwise
		 */
		public int compare(AgentEvent a1, AgentEvent a2) {
			if (a1.id == a2.id)
				return 0;
			else if (a1.id > a2.id)
				return 1;
			else
				return -1;
		}
	}

	/**
	 * Compares resource events
	 */
	class ResourceEventComparator implements Comparator<ResourceEvent> {
		/**
		 * Checks if two resourceEvents are the same by checking their ids.
		 *
		 * @param a1 The first resource event
		 * @param a2 The second resource event
		 * @return returns 0 if the two resource events are the same, 1 if the id of
		 * the resource event is bigger than the id of the second resource event,
		 * -1 otherwise
		 */
		public int compare(ResourceEvent a1, ResourceEvent a2) {
			if (a1.id == a2.id)
				return 0;
			else if (a1.id > a2.id)
				return 1;
			else
				return -1;
		}
	}

	/**
	 * Retrieves the total number of agents
	 *
	 * @return {@code totalAgents }
	 */
	public long totalAgents() {
		return totalAgents;
	}

	/**
	 * Retrieves the CityMap instance of this simulation
	 *
	 * @return {@code map }
	 */
	public CityMap getMap() {
		return map;
	}

	/**
	 * Sets the events of the simulation.
	 *
	 * @param events The PriorityQueue of events
	 */
	public void setEvents(PriorityQueue<Event> events) {
		this.events = events;
	}

	/**
	 * Retrieves the queue of events of the simulation.
	 *
	 * @return {@code events }
	 */
	public PriorityQueue<Event> getEvents() {
		return events;
	}

	/**
	 * Gets the empty agents in the simulation
	 *
	 * @return {@code emptyAgents }
	 */
	public TreeSet<AgentEvent> getEmptyAgents() {
		return emptyAgents;
	}

	/**
	 * Sets the empty agents in the simulation
	 *
	 * @param emptyAgents The TreeSet of agent events to set.
	 */
	public void setEmptyAgents(TreeSet<AgentEvent> emptyAgents) {
		this.emptyAgents = emptyAgents;
	}

	/**
	 * Make an agent copy of locationOnRoad so that an agent cannot modify the attributes of the road.
	 *
	 * @param locationOnRoad the location to make a copy for
	 * @return an agent copy of the location
	 */
	public LocationOnRoad agentCopy(LocationOnRoad locationOnRoad) {
		Intersection from = mapForAgents.intersections().get(locationOnRoad.road.from.id);
		Intersection to = mapForAgents.intersections().get(locationOnRoad.road.to.id);
		Road roadAgentCopy = from.roadsMapFrom.get(to);
		LocationOnRoad locationOnRoadAgentCopy = new LocationOnRoad(roadAgentCopy, locationOnRoad.travelTimeFromStartIntersection);
		return locationOnRoadAgentCopy;
	}
}