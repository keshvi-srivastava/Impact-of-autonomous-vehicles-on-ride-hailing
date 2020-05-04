# Impact of autonomous vehicles on ride hailing

Mobile agents search for stationary resources in a road network. Each resource can be obtained by only one agent at a time, therefore, the search is competitive. In this project, we determine how the assignment of resources to agents affects the performance of the system i.e. wait time of resources and empty cruise of agents. The assignment is fair for crowdsourced vehicles, and optimum for (owned) autonomous vehicles.

This project simulates crowdsourced taxicabs and autonmous taxicabs (called agents) searching for customers (called resources) to pick up in a NYC. The simulator works with two different approaches (fair and optimal) for each of the above taxicab scenarios and provides statistics on the simulation.

The project uses COMSET simulator as a the basic model. However, unlike COMSET, there is no sequential allotment of resources to agents, rather its in pools of 30/60 seconds.

# Setting Up IntelliJ

1. Download the project (clone/zip file) and open the pom.xml file as a project in IntelliJ. 
2. Update the required csv file for testing in the config.properties file.
3. Update the pools in the simulator.java class -> run() function. 
4. Run the main.java file.

# Output

The statistics provided for each run include various parameters. Example:
For a run for NYC data on 6th January 2014 will be as given below:

running time: 747 

***Simulation environment*** \
JSON map file: maps/manhattan-map.json\
Resource dataset file: datasets/yellow_tripdata_2016-06-01_busyhours.csv\
Bounding polygon KML file: maps/manhattan-boundary.kml\
Number of agents: 5000\
Number of resources: 237109\
Resource Maximum Life Time: 600 seconds\
Agent class: UserExamples.AgentRandomDestination

***Statistics***\
average agent search time: 324 seconds \
average resource wait time: 50 seconds \
resource expiration percentage: 2%

average agent cruise time: 222 seconds \
average agent approach time: 23 seconds \
average resource trip time: 853 seconds \
total number of assignments: 232316\
total pool time 6.79684497446E11\
avg pool time 4.1118239409921354E8\
average benefit per agent: 19.126739984798657
