package UserExamples;

public class StableMatching
{

    private int N, matchedCount;
    private int[][] resourcesPref;
    private int[][] agentsPref;
    private Integer[] resources;
    private Integer[] agents;
    private Integer[] agentsPartner;
    private boolean[] resourcesMatched;

    /** Constructor **/
    public StableMatching(Integer[] resources, Integer[] agents, int[][] agentsPref, int[][] resourcesPref)
    {
        N = resources.length>agents.length?agents.length:resources.length;
        matchedCount = 0;
        this.resources = resources;
        this.agents = agents;
        this.resourcesPref = resourcesPref;
        this.agentsPref = agentsPref;
        resourcesMatched = new boolean[N];
        agentsPartner = new Integer[N];
        calcMatches();
    }

    /** function to calculate all matches **/
    private void calcMatches()
    {
        try {
            while (matchedCount < N) {
                int free;
                for (free = 0; free < N; free++)
                    if (!resourcesMatched[free])
                        break;

                for (int i = 0; i < N && !resourcesMatched[free]; i++) {
                    int index = agentsIndexOf(resourcesPref[free][i]);
                    if (agentsPartner[index] == null) {
                        agentsPartner[index] = resources[free];
                        resourcesMatched[free] = true;
                        matchedCount++;
                    } else {
                        Integer currentPartner = agentsPartner[index];
                        if (morePreference(currentPartner, resources[free], index)) {
                            agentsPartner[index] = resources[free];
                            resourcesMatched[free] = true;
                            resourcesMatched[resourcesIndexOf(currentPartner)] = false;
                        }
                    }
                }
            }
        }
        catch(Exception e)
        {

        }

    }
    /** function to check if agents prefers new partner over old assigned partner **/
    private boolean morePreference(Integer curPartner, Integer newPartner, int index)
    {
        try {
            for (int i = 0; i < N; i++) {
                if (agentsPref[index][i] == (newPartner))
                    return true;
                if (agentsPref[index][i] == (curPartner))
                    return false;
            }
        }
        catch(Exception e)
        {

        }
        return false;
    }
    /** get resources index **/
    private int resourcesIndexOf(Integer str)
    {
        for (int i = 0; i < N; i++)
            if (resources[i] == str)
                return i;
        return -1;
    }
    /** get agents index **/
    private int agentsIndexOf(Integer str)
    {
        for (int i = 0; i < N; i++)
            if ((int)agents[i] == str)
                return i;
        return -1;
    }
    public Integer[] getMatches()
    {

        return agentsPartner;
    }

    }
