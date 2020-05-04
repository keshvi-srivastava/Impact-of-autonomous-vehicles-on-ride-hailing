package UserExamples;
/*
 * Created on Apr 25, 2005
 * Updated on May 2, 2013 (support for rectangular matrices)
 *
 * Konstantinos A. Nedas
 * Department of Spatial Information Science & Engineering
 * University of Maine, Orono, ME 04469-5711, USA
 * kostas@spatial.maine.edu
 * http://www.spatial.maine.edu/~kostas
 *
 * This Java class implements the Hungarian algorithm [a.k.a Munkres' algorithm,
 * a.k.a. Kuhn algorithm, a.k.a. Assignment problem, a.k.a. Marriage problem,
 * a.k.a. Maximum Weighted Maximum Cardinality Bipartite Matching].
 *
 * [It can be used as a method call from within any main (or other function).]
 * It takes two arguments:
 * a. A 2D array (could be rectangular or square) with all values >= 0.
 * b. A string ("min" or "max") specifying whether you want the min or max assignment.
 * [It returns an assignment matrix[min(array.length, array[0].length)][2] that contains
 * the row and col of the elements (in the original inputted array) that make up the
 * optimum assignment or the sum of the assignment weights, depending on which method
 * is used: hgAlgorithmAssignments or hgAlgorithm, respectively.]
 *
 * [This version contains only scarce comments. If you want to understand the
 * inner workings of the algorithm, get the tutorial version of the algorithm
 * from the same website you got this one (www.spatial.maine.edu/~kostas).]
 *
 * Any comments, corrections, or additions would be much appreciated.
 * Credit due to professor Bob Pilgrim for providing an online copy of the
 * pseudocode for this algorithm (http://216.249.163.93/bob.pilgrim/445/munkres.html)
 *
 * Feel free to redistribute this source code, as long as this header--with
 * the exception of sections in brackets--remains as part of the file.
 *
 * Note: Some sections in brackets have been modified as not to provide misinformation
 *       about the current functionality of this code.
 *
 * Requirements: JDK 1.5.0_01 or better.
 * [Created in Eclipse 3.1M6 (www.eclipse.org).]
 *
 * Reference git link : https://github.com/w01fe/hungarian
 */

import static java.lang.Math.*;
import java.util.*;

public class HungarianAlgorithm {

    //*******************************************//
    //METHODS THAT PERFORM ARRAY-PROCESSING TASKS//
    //*******************************************//

    public static void generateRandomArray	//Generates random 2-D array.
    (double[][] array, String randomMethod)
    {
        Random generator = new Random();
        for (int i=0; i<array.length; i++)
        {
            for (int j=0; j<array[i].length; j++)
            {
                if (randomMethod.equals("random"))
                {array[i][j] = generator.nextDouble();}
                if (randomMethod.equals("gaussian"))
                {
                    array[i][j] = generator.nextGaussian()/4;		//range length to 1.
                    if (array[i][j] > 0.5) {array[i][j] = 0.5;}		//eliminate outliers.
                    if (array[i][j] < -0.5) {array[i][j] = -0.5;}	//eliminate outliers.
                    array[i][j] = array[i][j] + 0.5;				//make elements positive.
                }
            }
        }
    }
    public static double findLargest		//Finds the largest element in a 2D array.
    (double[][] array)
    {
        double largest = Double.NEGATIVE_INFINITY;
        for (int i=0; i<array.length; i++)
        {
            for (int j=0; j<array[i].length; j++)
            {
                if (array[i][j] > largest)
                {
                    largest = array[i][j];
                }
            }
        }

        return largest;
    }
    public static double[][] transpose		//Transposes a double[][] array.
    (double[][] array)
    {
        double[][] transposedArray = new double[array[0].length][array.length];
        for (int i=0; i<transposedArray.length; i++)
        {
            for (int j=0; j<transposedArray[i].length; j++)
            {transposedArray[i][j] = array[j][i];}
        }
        return transposedArray;
    }
    public static double[][] copyOf			//Copies all elements of an array to a new array.
    (double[][] original)
    {
        double[][] copy = new double[original.length][original[0].length];
//        for (int i=0; i<original.length; i++)
//        {
//            //Need to do it this way, otherwise it copies only memory location
//
            //System.arraycopy(original[i], 0, copy[i], 0, original[i].length);
//        }

        copy = Arrays.stream(original).map(double[]::clone).toArray(double[][]::new);
        return copy;
    }
    public static double[][] copyToSquare	//Creates a copy of an array, made square by padding the right or bottom.
    (double[][] original, double padValue)
    {
        int rows = original.length;
        int cols = original[0].length;	//Assume we're given a rectangular array.
        double[][] result = null;

        if (rows == cols)	//The matrix is already square.
        {
            result = copyOf(original);
        }
        else if (rows > cols)	//Pad on some extra columns on the right.
        {
            result = new double[rows][rows];
            for (int i=0; i<rows; i++)
            {
                for (int j=0; j<rows; j++)
                {
                    if (j >= cols)	//Use the padValue to fill the right columns.
                    {
                        result[i][j] = padValue;
                    }
                    else
                    {
                        result[i][j] = original[i][j];
                    }
                }
            }
        }
        else
        {	// rows < cols; Pad on some extra rows at the bottom.
            result = new double[cols][cols];
            for (int i=0; i<cols; i++)
            {
                for (int j=0; j<cols; j++)
                {
                    if (i >= rows)	//Use the padValue to fill the bottom rows.
                    {
                        result[i][j] = padValue;
                    }
                    else
                    {
                        result[i][j] = original[i][j];
                    }
                }
            }
        }

        return result;
    }

    //**********************************//
    //METHODS OF THE HUNGARIAN ALGORITHM//
    //**********************************//

    //Core of the algorithm; takes required inputs and returns the assignments
    public static int[][] hgAlgorithmAssignments(double[][] array, String sumType)
    {
        //This variable is used to pad a rectangular array (so it will be picked all last [cost] or first [profit])
        //and will not interfere with final assignments.  Also, it is used to flip the relationship between weights
        //when "max" defines it as a profit matrix instead of a cost matrix.  Double.MAX_VALUE is not ideal, since arithmetic
        //needs to be performed and overflow may occur.
        double maxWeightPlusOne = findLargest(array) + 1;

        double[][] cost = copyToSquare(array, maxWeightPlusOne);	//Create the cost matrix

        if (sumType.equalsIgnoreCase("max"))	//Then array is a profit array.  Must flip the values because the algorithm finds lowest.
        {
            for (int i=0; i<cost.length; i++)		//Generate profit by subtracting from some value larger than everything.
            {
                for (int j=0; j<cost[i].length; j++)
                {
                    cost[i][j] = (maxWeightPlusOne - cost[i][j]);
                }
            }
        }

        int[][] mask = new int[cost.length][cost[0].length];	//The mask array.
        int[] rowCover = new int[cost.length];					//The row covering vector.
        int[] colCover = new int[cost[0].length];				//The column covering vector.
        int[] zero_RC = new int[2];								//Position of last zero from Step 4.
        int[][] path = new int[cost.length * cost[0].length + 2][2];
        int step = 1;
        boolean done = false;
        while (done == false)	//main execution loop
        {
            switch (step)
            {
                case 1:
                    step = hg_step1(step, cost);
                    break;
                case 2:
                    step = hg_step2(step, cost, mask, rowCover, colCover);
                    break;
                case 3:
                    step = hg_step3(step, mask, colCover);
                    break;
                case 4:
                    step = hg_step4(step, cost, mask, rowCover, colCover, zero_RC);
                    break;
                case 5:
                    step = hg_step5(step, mask, rowCover, colCover, zero_RC, path);
                    break;
                case 6:
                    step = hg_step6(step, cost, rowCover, colCover);
                    break;
                case 7:
                    done=true;
                    break;
            }
        }//end while

        int[][] assignments = new int[array.length][2];	//Create the returned array.
        int assignmentCount = 0;	//In a input matrix taller than it is wide, the first
        //assignments column will have to skip some numbers, so
        //the index will not always match the first column ([0])
        for (int i=0; i<mask.length; i++)
        {
            for (int j=0; j<mask[i].length; j++)
            {
                if (i < array.length && j < array[0].length && mask[i][j] == 1)
                {
                    assignments[assignmentCount][0] = i;
                    assignments[assignmentCount][1] = j;
                    assignmentCount++;
                }
            }
        }

        return assignments;
    }
    //Calls hgAlgorithmAssignments and getAssignmentSum to compute the
    //minimum cost or maximum profit possible.
    public static double hgAlgorithm(double[][] array, String sumType)
    {
        return getAssignmentSum(array, hgAlgorithmAssignments(array, sumType));
    }
    public static double getAssignmentSum(double[][] array, int[][] assignments) {
        //Returns the min/max sum (cost/profit of the assignment) given the
        //original input matrix and an assignment array (from hgAlgorithmAssignments)
        double sum = 0;
        for (int i=0; i<assignments.length; i++)
        {
            sum = sum + array[assignments[i][0]][assignments[i][1]];
        }
        return sum;
    }
    public static int hg_step1(int step, double[][] cost)
    {
        //What STEP 1 does:
        //For each row of the cost matrix, find the smallest element
        //and subtract it from from every other element in its row.

        double minval;

        for (int i=0; i<cost.length; i++)
        {
            minval=cost[i][0];
            for (int j=0; j<cost[i].length; j++)	//1st inner loop finds min val in row.
            {
                if (minval>cost[i][j])
                {
                    minval=cost[i][j];
                }
            }
            for (int j=0; j<cost[i].length; j++)	//2nd inner loop subtracts it.
            {
                cost[i][j]=cost[i][j]-minval;
            }
        }

        step=2;
        return step;
    }
    public static int hg_step2(int step, double[][] cost, int[][] mask, int[] rowCover, int[] colCover)
    {
        //What STEP 2 does:
        //Marks uncovered zeros as starred and covers their row and column.

        for (int i=0; i<cost.length; i++)
        {
            for (int j=0; j<cost[i].length; j++)
            {
                if ((cost[i][j]==0) && (colCover[j]==0) && (rowCover[i]==0))
                {
                    mask[i][j]=1;
                    colCover[j]=1;
                    rowCover[i]=1;
                }
            }
        }

        clearCovers(rowCover, colCover);	//Reset cover vectors.

        step=3;
        return step;
    }
    public static int hg_step3(int step, int[][] mask, int[] colCover)
    {
        //What STEP 3 does:
        //Cover columns of starred zeros. Check if all columns are covered.

        for (int i=0; i<mask.length; i++)	//Cover columns of starred zeros.
        {
            for (int j=0; j<mask[i].length; j++)
            {
                if (mask[i][j] == 1)
                {
                    colCover[j]=1;
                }
            }
        }

        int count=0;
        for (int j=0; j<colCover.length; j++)	//Check if all columns are covered.
        {
            count=count+colCover[j];
        }

        if (count>=mask.length)	//Should be cost.length but ok, because mask has same dimensions.
        {
            step=7;
        }
        else
        {
            step=4;
        }

        return step;
    }
    public static int hg_step4(int step, double[][] cost, int[][] mask, int[] rowCover, int[] colCover, int[] zero_RC)
    {
        //What STEP 4 does:
        //Find an uncovered zero in cost and prime it (if none go to step 6). Check for star in same row:
        //if yes, cover the row and uncover the star's column. Repeat until no uncovered zeros are left
        //and go to step 6. If not, save location of primed zero and go to step 5.

        int[] row_col = new int[2];	//Holds row and col of uncovered zero.
        boolean done = false;
        while (done == false)
        {
            row_col = findUncoveredZero(row_col, cost, rowCover, colCover);
            if (row_col[0] == -1)
            {
                done = true;
                step = 6;
            }
            else
            {
                mask[row_col[0]][row_col[1]] = 2;	//Prime the found uncovered zero.

                boolean starInRow = false;
                for (int j=0; j<mask[row_col[0]].length; j++)
                {
                    if (mask[row_col[0]][j]==1)		//If there is a star in the same row...
                    {
                        starInRow = true;
                        row_col[1] = j;		//remember its column.
                    }
                }

                if (starInRow==true)
                {
                    rowCover[row_col[0]] = 1;	//Cover the star's row.
                    colCover[row_col[1]] = 0;	//Uncover its column.
                }
                else
                {
                    zero_RC[0] = row_col[0];	//Save row of primed zero.
                    zero_RC[1] = row_col[1];	//Save column of primed zero.
                    done = true;
                    step = 5;
                }
            }
        }

        return step;
    }
    public static int[] findUncoveredZero	//Aux 1 for hg_step4.
    (int[] row_col, double[][] cost, int[] rowCover, int[] colCover)
    {
        row_col[0] = -1;	//Just a check value. Not a real index.
        row_col[1] = 0;

        int i = 0; boolean done = false;
        while (done == false)
        {
            int j = 0;
            while (j < cost[i].length)
            {
                if (cost[i][j]==0 && rowCover[i]==0 && colCover[j]==0)
                {
                    row_col[0] = i;
                    row_col[1] = j;
                    done = true;
                }
                j = j+1;
            }//end inner while
            i=i+1;
            if (i >= cost.length)
            {
                done = true;
            }
        }//end outer while

        return row_col;
    }
    public static int hg_step5(int step, int[][] mask, int[] rowCover, int[] colCover, int[] zero_RC, int [][] path)
    {
        //What STEP 5 does:
        //Construct series of alternating primes and stars. Start with prime from step 4.
        //Take star in the same column. Next take prime in the same row as the star. Finish
        //at a prime with no star in its column. Unstar all stars and star the primes of the
        //series. Erasy any other primes. Reset covers. Go to step 3.

        int count = 0;										//Counts rows of the path matrix.
        //int[][] path = new int[(mask[0].length + 2)][2];	//Path matrix (stores row and col).
        path[count][0] = zero_RC[0];						//Row of last prime.
        path[count][1] = zero_RC[1];						//Column of last prime.

        boolean done = false;
        while (done == false)
        {
            int r = findStarInCol(mask, path[count][1]);
            if (r>=0)
            {
                count = count+1;
                path[count][0] = r;					//Row of starred zero.
                path[count][1] = path[count-1][1];	//Column of starred zero.
            }
            else
            {
                done = true;
            }

            if (done == false)
            {
                int c = findPrimeInRow(mask, path[count][0]);
                count = count+1;
                path[count][0] = path[count-1][0];	//Row of primed zero.
                path[count][1] = c;					//Col of primed zero.
            }
        }//end while

        convertPath(mask, path, count);
        clearCovers(rowCover, colCover);
        erasePrimes(mask);

        step = 3;
        return step;

    }
    public static int findStarInCol			//Aux 1 for hg_step5.
    (int[][] mask, int col)
    {
        int r = -1;	//Again this is a check value.
        for (int i=0; i<mask.length; i++)
        {
            if (mask[i][col]==1)
            {
                r = i;
            }
        }

        return r;
    }
    public static int findPrimeInRow		//Aux 2 for hg_step5.
    (int[][] mask, int row)
    {
        int c = -1;
        for (int j=0; j<mask[row].length; j++)
        {
            if (mask[row][j]==2)
            {
                c = j;
            }
        }

        return c;
    }
    public static void convertPath			//Aux 3 for hg_step5.
    (int[][] mask, int[][] path, int count)
    {
        for (int i=0; i<=count; i++)
        {
            if (mask[path[i][0]][path[i][1]]==1)
            {
                mask[path[i][0]][path[i][1]] = 0;
            }
            else
            {
                mask[path[i][0]][path[i][1]] = 1;
            }
        }
    }
    public static void erasePrimes			//Aux 4 for hg_step5.
    (int[][] mask)
    {
        for (int i=0; i<mask.length; i++)
        {
            for (int j=0; j<mask[i].length; j++)
            {
                if (mask[i][j]==2)
                {
                    mask[i][j] = 0;
                }
            }
        }
    }
    public static void clearCovers			//Aux 5 for hg_step5 (and not only).
    (int[] rowCover, int[] colCover)
    {
        for (int i=0; i<rowCover.length; i++)
        {
            rowCover[i] = 0;
        }
        for (int j=0; j<colCover.length; j++)
        {
            colCover[j] = 0;
        }
    }
    public static int hg_step6(int step, double[][] cost, int[] rowCover, int[] colCover)
    {
        //What STEP 6 does:
        //Find smallest uncovered value in cost: a. Add it to every element of covered rows
        //b. Subtract it from every element of uncovered columns. Go to step 4.

        double minval = findSmallest(cost, rowCover, colCover);

        for (int i=0; i<rowCover.length; i++)
        {
            for (int j=0; j<colCover.length; j++)
            {
                if (rowCover[i]==1)
                {
                    cost[i][j] = cost[i][j] + minval;
                }
                if (colCover[j]==0)
                {
                    cost[i][j] = cost[i][j] - minval;
                }
            }
        }

        step = 4;
        return step;
    }
    public static double findSmallest		//Aux 1 for hg_step6.
    (double[][] cost, int[] rowCover, int[] colCover)
    {
        double minval = Double.POSITIVE_INFINITY;	//There cannot be a larger cost than this.
        for (int i=0; i<cost.length; i++)		//Now find the smallest uncovered value.
        {
            for (int j=0; j<cost[i].length; j++)
            {
                if (rowCover[i]==0 && colCover[j]==0 && (minval > cost[i][j]))
                {
                    minval = cost[i][j];
                }
            }
        }

        return minval;
    }

    public static void set(double [][] arr, int i, int j, double v) {arr[i][j] = v;}

    //***********//
    //MAIN METHOD//
    //***********//

    public static void main(String[] args) {
        System.out.println("Running two tests on three arrays:\n");

        // Square
        double[][] test1 = {{0.43517016603212244, 0.5300479467355548, 0.7353617329276672, 0.4301428288655441, 0.5109112483177601, 0.6450513663161894, 0.6450513663161894, 0.48244114748320344, 0.4615980922106083, 0.4512242959933178, 0.5384554667553098, 0.7372607585476053, 0.47494500874030454, 0.6823180356247472, 0.5362228047086786, 0.42701736877442, 0.5133343305510281, 0.44123807347262844, 0.5559689252937784, 0.5270640030179623, 0.4627536046437392, 0.6059602734925806, 0.46960573032792174, 0.5497289231059278, 0.47522143629953006, 0.609413176414822, 0.42092302004599297, 0.6782133525324112, 0.6914314101570674, 0.6999273483652358, 0.5093154279232649, 0.5076329720003826, 0.4860639934109363, 0.5270640030179623, 0.4378584325600187, 0.4825766315757293, 0.41164750260963595, 0.48790692406763936, 0.5423563645100042, 0.6302312295126723, 0.6823180356247472, 0.5488412157192648, 0.48343377930829023, 0.5000868899152822, 0.4555014843201094, 0.7372607585476053, 0.48770666458585393, 0.5231592934794227, 0.7180049867554247, 0.542740546333953, 0.6194143241498711}, {0.4512012038116989, 0.7208363710333296, 0.5044535827632091, 0.19812657025921504, 0.6716816004322861, 0.6657314761057859, 0.6657314761057859, 0.5690161482588467, 0.2498444759529857, 0.44872447791716347, 0.6779970772475669, 0.40446464488489364, 0.46766279339755973, 0.6331923540619485, 0.47195873652602705, 0.4402285974048091, 0.5395504503654625, 0.4573109721590635, 0.6340375113292797, 0.5654168877873844, 0.47293211917139377, 0.36029080219933, 0.5197518911204212, 0.26567018737846, 0.3383093432049281, 0.42380153920549885, 0.4293290823837819, 0.34615516451201844, 0.4161612361083905, 0.33592926410741886, 0.34810813735784585, 0.44556251751138654, 0.5260745014241954, 0.5654168877873844, 0.47509739925863875, 0.5522060102620381, 0.41420660299569634, 0.2456873609435299, 0.24690578385049305, 0.7596602898508202, 0.6331923540619485, 0.2536137375514536, 0.3375392820249633, 0.3861659712612827, 0.22976367877975837, 0.40446464488489364, 0.4911862525240511, 0.276850622409978, 0.4963016008262287, 0.7795251499504556, 0.2825791845372332}, {0.9854780363280119, 0.6199955991314406, 0.38310769630744695, 0.21518440652798446, 0.5691016234356443, 0.4750043936790342, 0.4750043936790342, 0.686854837351984, 0.2828639817286101, 0.7428898393963449, 0.5034435416787495, 0.3652903016726453, 0.6542290222918012, 0.43413062967611504, 0.5091321534255424, 0.5656971337653521, 0.6157384261546229, 0.518918389172618, 0.46035313969216113, 0.4996014581563357, 0.7397415699356361, 0.36234994271843113, 0.8014412164868777, 0.2769543363278146, 0.40994324692599665, 0.41960737276105303, 0.4128825981692209, 0.8481453788176121, 0.33314705252227045, 0.38117482085797916, 0.32095824943365275, 0.39543167262231826, 0.5250252049926996, 0.7067679807459453, 0.59836423743345, 0.6658763152809392, 0.7530839169299021, 0.5875340003376897, 0.26787308794263714, 0.2547446497024891, 0.48262342362892396, 0.43413062967611504, 0.2620239596053594, 0.40481159588537324, 0.45549872019449117, 0.2555768201667065, 0.3652903016726453, 0.644477191077773, 0.29800283481751355, 0.4120135839987777, 0.5926837758794798, 0.28171454596946}, {0.3233282235023863, 0.43335040312637224, 0.5565704308988312, 0.25460117535872206, 0.38843869906876133, 0.6173350480116888, 0.6173350480116888, 0.35584356091709196, 0.32105831874113183, 0.3469714938278948, 0.40724525977366666, 0.7096965472846216, 0.3780129700179254, 0.5898136250666591, 0.46636385154764115, 0.2959388325598374, 0.4278642601853493, 0.3035054346073844, 0.46072519805404843, 0.4904468815327043, 0.3603718553234754, 0.5205654686897918, 0.3640795114148574, 0.3730311289049554, 0.372879577863226, 0.5130288566998351, 0.5720001321829415, 0.30577086533877784, 0.548815133914875, 0.6868481899847713, 0.5328494509834297, 0.4133594371118152, 0.42459834339152874, 0.38824494917857993, 0.44663505331699155, 0.31530521018261964, 0.3793674787924689, 0.286325360677252, 0.3285758566478483, 0.3404444655999663, 0.560954311799343, 0.5898136250666591, 0.35314382533486, 0.37621048128073187, 0.4111698821063861, 0.2997877017789863, 0.7096965472846216, 0.39414972071961, 0.37748681742406576, 0.8360506471493215, 0.44739381747496504, 0.41169037730181807}, {0.5817512342892662, 0.6822929006573136, 0.8030174414442497, 0.413963423810971, 0.7336758496413975, 0.7093734741344265, 0.7093734741344265, 0.6620923929712792, 0.4478842650757075, 0.5692130779747424, 0.7976486405180431, 0.6110146629823183, 0.5771120194513012, 0.7863245039905166, 0.5878232045256474, 0.6351030382064243, 0.6122032897537559, 0.6648566727354256, 0.8480947100611628, 0.5969332040542424, 0.5818252956171077, 0.5521458193236083, 0.6097802689548097, 0.48688911043992705, 0.5079644425504999, 0.8539875712661967, 0.5858837121998972, 0.5769996725813977, 0.5641210883804808, 0.6052318768887036, 0.5651926526966726, 0.519702128461142, 0.5695508698783781, 0.606958896223279, 0.6240656954218144, 0.6315420993293567, 0.623155932612067, 0.6087930849229541, 0.45481233855167696, 0.47989604449751594, 0.7478860528762079, 0.7863245039905166, 0.48336762646361403, 0.5052672289020663, 0.5381460518476627, 0.43395999059226265, 0.6110146629823183, 0.5890762463787628, 0.48302947428230525, 0.6593753480572456, 0.6983095841594869, 0.5199120692948954}, {0.6518238727236851, 0.7592479495984874, 0.5287336160675381, 0.28811052388253044, 0.66891576322383, 0.5952172255535013, 0.6563597653378835, 0.6854192193538233, 0.6491174395509807, 0.3842401152153639, 0.6946353441031974, 0.5890905829297394, 0.5861551145953467, 0.5242179037758714, 0.7330417928664885, 0.8319005842693119, 0.6141241949529644, 0.762654559764752, 0.5144479722086672, 0.9398738060962217, 0.5077600388538043, 0.5759565807588033, 0.7577449087157201, 0.7626383952305513, 0.5189161106313737, 0.754245582604229, 0.3816095164721116, 0.5654072455943147, 0.5399334629583274, 0.7095947368078473, 0.6009223879313125, 0.729115301572774, 0.4719136616203306, 0.5527283026916576, 0.45141298952138503, 0.551436075704924, 0.7330417928664885, 0.8383145296914187, 0.9723061069148545, 0.5757138432509233, 0.7846477153119474, 0.5111715411340852, 0.3658214639757871, 0.3473114410405058, 0.6655265006206061, 0.5890905829297394, 0.3586305967328229, 0.4620910814586563, 0.5497122288547386, 0.3528613863583976, 0.6345775286754762, 0.3492343764699632, 0.5242179037758714, 0.8013218454698647, 0.4119690696868195, 0.6000037319164085, 0.7344544757093245, 0.388859768142111}, {0.3502706722058942, 0.45623371083042685, 0.6518233733068295, 0.3384943420027886, 0.4509648463606237, 0.4953634116485308, 0.5905282030041547, 0.5676484861286755, 0.38608849041325527, 0.37279700793967785, 0.35644475178048046, 0.6061753204475364, 0.4493189556368069, 0.6742432361614444, 0.40760689040127407, 0.48883765900327547, 0.5862128596920498, 0.4510536662043696, 0.33855376635815343, 0.4273540875079133, 0.34916402390723306, 0.5058243114060212, 0.4672037278638027, 0.3770840776003326, 0.5223733573523925, 0.38343989999776446, 0.45603392577227453, 0.3872029015578227, 0.5246674282603926, 0.5229898760604198, 0.3366894973827764, 0.5032111876814351, 0.5985741676023374, 0.6208054513385665, 0.6197314286389363, 0.40954747564484734, 0.40760689040127407, 0.3998535929020354, 0.44131055760805976, 0.3589535768000799, 0.3961175834916468, 0.3272033091350625, 0.3962873373148375, 0.4450527427068952, 0.5495038689624985, 0.6061753204475364, 0.45260633756429713, 0.6100953222206879, 0.3913781466399647, 0.4491969853015748, 0.4140676824022069, 0.363024215832549, 0.6742432361614444, 0.38857987964674745, 0.43255421571609004, 0.6549146639504337, 0.4698049833166927, 0.5249291393692319}, {0.8151867533260029, 0.49512424546379025, 0.3381971093985535, 0.1781412837932054, 0.49472443579656666, 0.4228898568931502, 0.38101869426377377, 0.39153426781748263, 0.6420934103178262, 0.22829108146707788, 0.5456496252713192, 0.3693660361310692, 0.45919896475338945, 0.2980263429308168, 0.4074439331110221, 0.4345194908200646, 0.38170458458845263, 0.39574827064104284, 0.6285692572798487, 0.4729221203081185, 0.5615006813191302, 0.407156168261485, 0.39050153426196105, 0.5455833748049921, 0.29090291380726646, 0.603377612802501, 0.2262803474336001, 0.32826832710977594, 0.37859008192390187, 0.37201235488074713, 0.8151867533260029, 0.3784222661642357, 0.27134455288894965, 0.30876790409936167, 0.2628536224997333, 0.3139049547374854, 0.4074439331110221, 0.5330465281538556, 0.46441418505063403, 0.6889047750676979, 0.5807761365984659, 0.6859930502967396, 0.21789195678139842, 0.21023157419753885, 0.40564418090623544, 0.3693660361310692, 0.215557833526792, 0.2672937857246442, 0.3178338084159895, 0.2128495889918304, 0.3545933164518164, 0.20962486193626473, 0.2980263429308168, 0.5405188071549208, 0.24092320800085304, 0.3365210640701462, 0.4776760193980297, 0.2319223699076863}, {0.22225419661827062, 0.3552330513087899, 0.26471872263628315, 0.11730344034811249, 0.29631323476778654, 0.27289888062971385, 0.409892925285721, 0.44169181505429655, 0.2546932507133449, 0.17947032903455737, 0.2813227334257779, 0.31231930339571845, 0.2503389190122591, 0.32308671240947356, 0.4255833755133092, 0.5397932156921359, 0.3318487386393221, 0.6023857258436217, 0.18586504987985053, 0.44931206376093813, 0.18735504955633553, 0.2640652900647479, 0.6934998446130358, 0.31580899753521857, 0.3222689093602433, 0.3020394504196929, 0.18091308500359737, 0.3123605038775423, 0.24602615554396706, 0.7727097807510983, 0.22225419661827062, 0.870216579144372, 0.2644028234738026, 0.3688829201509261, 0.2413962451649061, 0.32126207810459306, 0.4255833755133092, 0.35437477084325636, 0.48213141627392453, 0.21595845471593317, 0.3201393494112283, 0.18392205837316353, 0.16820024658182667, 0.15481139668041596, 0.3696277161747529, 0.31231930339571845, 0.16315072322458124, 0.25313617537060434, 0.3054925126701159, 0.15886313932202456, 0.3956977814391307, 0.15562001150533467, 0.32308671240947356, 0.33539772423145786, 0.20480946584391535, 0.4164808986192181, 0.35302506918911997, 0.18558582907724616}, {0.5145179199255169, 0.8524711657585445, 0.5607114725929103, 0.23452858627019008, 0.9080499969606954, 0.7552682602090927, 0.6560215157714806, 0.6728057383003441, 0.6721560556015442, 0.29243978799548265, 0.5042326923297806, 0.6437966599146947, 0.7481362670771274, 0.44500843603739404, 0.49086439881237826, 0.6798460493556693, 0.6776428770628756, 0.5260029891000692, 0.5118152903084113, 0.6049388266233248, 0.5374887931867325, 0.71039192403546, 0.5351427266021684, 0.5501701110131338, 0.4053898553295159, 0.6059848460122179, 0.3072756196441032, 0.3961659036838833, 0.6341776671295508, 0.5507928528687066, 0.5145179199255169, 0.5477546633237335, 0.3889664203348453, 0.4573363901554344, 0.378271106410255, 0.39429601821912574, 0.49086439881237826, 0.6034356772049873, 0.6265913871340727, 0.5875466222187543, 0.6387454182000972, 0.4996783587590169, 0.28685516275257095, 0.28695622874422133, 0.7399674224765197, 0.6437966599146947, 0.29416794508396804, 0.38406183023742557, 0.38862395939428257, 0.29054748776537426, 0.4407815274568123, 0.2710511932653864, 0.44500843603739404, 0.5767793219968436, 0.32018593253301597, 0.5294333527253441, 0.898039387133401, 0.32415637981530426}, {0.3175290004268679, 0.3777775613697194, 0.26329966929511356, 0.14605852401323755, 0.32409441562814173, 0.2865600384509505, 0.3512928952758592, 0.3702673674040118, 0.32177972793381693, 0.23034996802867122, 0.452305245616278, 0.2977149658320971, 0.277975374662471, 0.3065377207245426, 0.9642058776526989, 0.4646447325541663, 0.31191534785291636, 0.6895272559557738, 0.24477348932498794, 0.5706623920516497, 0.23774140665851867, 0.27685532403246904, 0.6217622278168358, 0.480569273022253, 0.33774589802424515, 0.4146010017783212, 0.21286958985272347, 0.4887392634814853, 0.2587166216951995, 0.4809496291849195, 0.3175290004268679, 0.5196921233747531, 0.2758822358724199, 0.33807220970103213, 0.25514404793807516, 0.44430728244395146, 0.9642058776526989, 0.48799009217193046, 0.5480521713888472, 0.278657331090927, 0.41724918263096056, 0.24517124319342476, 0.20741617540484295, 0.18291876606166818, 0.3408392549013939, 0.2977149658320971, 0.19216112901364968, 0.26563795157354114, 0.45499857715970227, 0.18738404156800503, 0.6163785596912311, 0.20032354267301297, 0.3065377207245426, 0.48799009217193046, 0.24677821950274412, 0.34799301253753384, 0.36483288366894223, 0.21029641212904576}, {0.31240375367289125, 0.4356626779717553, 0.6267469872172479, 0.29998663595970754, 0.42748341056383804, 0.4699608731511545, 0.5854115389056882, 0.560325062873639, 0.3527128557401976, 0.34319654446650644, 0.5544510005790894, 0.33378926876619713, 0.5916577851817615, 0.4221498079942695, 0.677216192668447, 0.3877104773310056, 0.44757247104674663, 0.5726387566705282, 0.4342184756945177, 0.30631090291531093, 0.40802753108965417, 0.2828005563291005, 0.47926056939249345, 0.4517941712441546, 0.3549341911657657, 0.5047224070317946, 0.3504766955185317, 0.41833058776982407, 0.365350471995971, 0.4947708562881663, 0.5137436424543664, 0.31240375367289125, 0.49155666151713356, 0.5758497647539864, 0.6202114060235663, 0.5889733436757285, 0.3880196825229753, 0.3877104773310056, 0.36678851448912747, 0.4228647570682469, 0.33295090004954864, 0.38513022143471576, 0.3009504591411064, 0.4740177843735738, 0.36243505994933434, 0.4002734089849147, 0.5356016947465667, 0.5726387566705282, 0.40971319388590854, 0.5840122866311841, 0.369340456871513, 0.4052704485171414, 0.39409871415409337, 0.32849452594181444, 0.677216192668447, 0.5969772076238238, 0.36678851448912747, 0.4020557116245254, 0.66834754990732, 0.44959008425526714, 0.47966481763953167, 0.4373238009073299}, {0.26625338267789433, 0.40602039714087057, 0.7829148682687739, 0.20581463458816807, 0.40945109192056317, 0.48291634812722006, 0.5803469317676977, 0.5403793741248173, 0.3140723907733818, 0.2387862456921421, 0.5821727822192462, 0.27742776980583206, 0.6735703278664044, 0.41448864198660457, 0.47425446359102674, 0.3126708138347107, 0.40791019092693237, 0.6234637433633895, 0.35429399786775606, 0.2687516513979466, 0.34879445422217087, 0.24469973693174504, 0.5012074868129883, 0.37007038836318684, 0.30692234655174994, 0.3608323565339407, 0.3003467850501719, 0.2832890211245198, 0.2772789885095262, 0.5318922175197612, 0.4253436736420594, 0.26625338267789433, 0.4056227306591303, 0.3906195306921699, 0.45228947160371463, 0.39323111998956617, 0.2904632081633593, 0.3126708138347107, 0.3120475890814819, 0.36491917200853835, 0.296222534572108, 0.33977447223303076, 0.26228944483514316, 0.4239428730726444, 0.24801061922464693, 0.2715346456458091, 0.5803469317676977, 0.6234637433633895, 0.2771684805393665, 0.392721199889338, 0.2778646330483162, 0.2744894026231419, 0.30596866469955275, 0.22624823325079266, 0.47425446359102674, 0.49662421940629803, 0.3120475890814819, 0.2773267198940271, 0.5509893891719146, 0.42644741475171555, 0.32222139450074616, 0.4403653965204301}, {0.16898028457743455, 0.2265444591555987, 0.2932938802924964, 0.2609418212514152, 0.21780864703781158, 0.23207729086360407, 0.29362853703026787, 0.2861346818022722, 0.18586345292266404, 0.2933826892339028, 0.2725073339526719, 0.18757247601975027, 0.2828395819466089, 0.21155170116613065, 0.47807431688330837, 0.23144086050085688, 0.23716908789059699, 0.2776115252372891, 0.2577133108633596, 0.16047509890682532, 0.22764572934265812, 0.14846373167164936, 0.23508940315273055, 0.2673176519420229, 0.20854607857644894, 0.4038683065239487, 0.191519131308974, 0.46579146640795926, 0.2408139377922398, 0.24039774368057049, 0.2994408716785822, 0.16898028457743455, 0.28830303547155156, 0.5926406413675535, 0.42566960518603825, 0.7323089332681462, 0.26633868575886294, 0.23144086050085688, 0.203214486828339, 0.23435454640485223, 0.17423088402309067, 0.2069112500138093, 0.1582797149947106, 0.2558129570256035, 0.3469181377529872, 0.4267978055784782, 0.29362853703026787, 0.2776115252372891, 0.45494435036769476, 0.6525189711991456, 0.24781335640818478, 0.44174536223358896, 0.2509469440584649, 0.29089087619824455, 0.5050762626362951, 0.33732746041343814, 0.203214486828339, 0.3635699701373082, 0.3643568319199307, 0.23152226044320692, 0.6332102259203637, 0.21682534586940527}, {0.1517635249672206, 0.19793066273777524, 0.20445163303241398, 0.05639773937714497, 0.2877310142209259, 0.33914878840867596, 0.18506551216798425, 0.1838817830681673, 0.21025903847492766, 0.15017605898476546, 0.07003301370641951, 0.21701881118766658, 0.12460752218566133, 0.21735833942550029, 0.4012857860355607, 0.11719800443342482, 0.11875855732578078, 0.19084100935124582, 0.21735833942550029, 0.12791058114452253, 0.1659310438575066, 0.20229186239030802, 0.15017605898476546, 0.17596350563351687, 0.3363985291028214, 0.13066020445669232, 0.13439957144071757, 0.08802565570604165, 0.1554820139604748, 0.07684753191483039, 0.10121913417271027, 0.30801949837240833, 0.13754763162453468, 0.1517635249672206, 0.13551730790629452, 0.10001462112184295, 0.14205251786869105, 0.09865732120114547, 0.09804296583244895, 0.06142398265327388, 0.13439957144071757, 0.1481685300850293, 0.16068487778811702, 0.22331631841689212, 0.11978503577545124, 0.18043333614069712, 0.14160207419591875, 0.1887054525538105, 0.1749050624672899, 0.07012439550287891, 0.07331182800913437, 0.1849976280277098, 0.21735833942550029, 0.07467848064140117, 0.09944009851315333, 0.09291956926334362, 0.07400488297029177, 0.05851904077892235, 0.26902322998903877, 0.1056181741389603, 0.06547538219565821, 0.23421318826838597, 0.11655814041661422, 0.14157665404672282, 0.1481685300850293, 0.07811823428216728, 0.14201489777156118, 0.24281327900010566, 0.08389322621970245, 0.46944141169531217}, {0.6451147982747258, 0.738482388577197, 0.5265889226495991, 0.2787164879556643, 0.6494165379069904, 0.5757426276050891, 0.5966949742828934, 0.6430479111254662, 0.6683164977019959, 0.9271533370658501, 0.3923557867103774, 0.6284852346658536, 0.7415973712257761, 0.5846567866168129, 0.5892612348246101, 0.5105365563715251, 0.7644627461873457, 0.7714891602415147, 0.5846567866168129, 0.7513690886763409, 0.4889045064595759, 0.5331466524600159, 0.9271533370658501, 0.4816253659370013, 0.5577356722236054, 0.735609653436297, 0.8906661638996225, 0.46032044138799316, 0.7911555969929027, 0.38369268182855243, 0.643186696981225, 0.5244338830612798, 0.675244750216508, 0.6451147982747258, 0.696062198538063, 0.4657433987744915, 0.5562471522876807, 0.5926184590067359, 0.4458323934034406, 0.5922280203212709, 0.3175272678948574, 0.8906661638996225, 0.8873777668986104, 0.8373322900418831, 0.5900660818882985, 0.4813832222341062, 0.8126279858660911, 0.5562471522876807, 0.5333186217162658, 0.7278593833830582, 0.375095905766561, 0.3493054542120923, 0.6189163167511116, 0.5846567866168129, 0.36052921969071444, 0.45615844331265687, 0.5736664712009426, 0.35479745698188775, 0.29297059605577874, 0.6678824434103219, 0.6485523081926644, 0.3620635017650788, 0.5279804530950426, 0.4978350218550119, 0.6324595760284563, 0.8873777668986104, 0.420739743789625, 0.5740214728952173, 0.6732996674034544, 0.38813274829582883, 0.5441797039515301}, {0.3237434667056548, 0.4724900567098622, 0.34167865231138655, 0.17017508751952645, 0.3692958823024477, 0.3399065878097021, 0.4223435048160304, 0.4640030987715398, 0.34054440190894075, 0.5663943398228527, 0.2858074889740124, 0.41616545613373707, 0.40302188333093847, 0.3856818491689557, 0.32785144590869, 0.40836614903071866, 0.6781122153718836, 0.4822774202979559, 0.3856818491689557, 0.8565873972251212, 0.3233648353633028, 0.26676826962744143, 0.5663943398228527, 0.2383749457730209, 0.3307675346540065, 0.8417269291371506, 0.5137960469794124, 0.3716199646306988, 0.4058418416148707, 0.2719088827396533, 0.5868248869781878, 0.3126407138335573, 0.6605373871295517, 0.3237434667056548, 0.7179023538408301, 0.3633060936692647, 0.43578716004013884, 0.49184120782370483, 0.33374103100656055, 0.5810356663603748, 0.20640527970979114, 0.5137960469794124, 0.46430893694043, 0.5714205963839407, 0.3019916073821119, 0.35728245004739495, 0.4314136607851142, 0.43578716004013884, 0.26572325511451195, 0.5293580129031152, 0.26605864125243833, 0.2325493245347417, 0.44260289936849656, 0.3856818491689557, 0.24478850838082716, 0.3486857274408524, 0.5202333222002276, 0.23846804907090094, 0.1829106808056056, 0.37427450908760357, 0.717547875663263, 0.25239670759341537, 0.26670291121000134, 0.3881188422401038, 0.5641838161222577, 0.46430893694043, 0.32017939162403414, 0.4622902699546987, 0.408912865525053, 0.27032951981786013, 0.30967673100822385}, {0.3773222677968144, 0.4526921065479098, 0.46836496812023887, 0.5705597264901493, 0.42029885281159557, 0.4268834861973749, 0.48687463143587245, 0.48264905248903034, 0.3892341143355713, 0.4578419896820127, 0.756622850460369, 0.4628698759505038, 0.4080518972522641, 0.46605707540935915, 0.4068260774884217, 0.5941545713807127, 0.4763964361348036, 0.44953009642466546, 0.46463881163032195, 0.4953830054493478, 0.4893052159411591, 0.3555522460227297, 0.4533430504662762, 0.3379604856527856, 0.4278567489474116, 0.5012411293842571, 0.43872759971440123, 0.7100162975862433, 0.40910471785863606, 0.8434557627533613, 0.49674692169810136, 0.4293107376064274, 0.5172651043512496, 0.3773222677968144, 0.5123170864944113, 0.661843637929386, 0.5385968588503899, 0.5335737688840944, 0.6621300237485329, 0.5396258641347829, 0.7584800632315096, 0.43872759971440123, 0.42642196821380646, 0.46215049110151196, 0.3767914962337639, 0.5797135100069963, 0.4255499083947104, 0.5364367148508581, 0.35357995236969353, 0.4736805531100269, 0.9058258352291724, 0.7238197985476341, 0.48497033724988786, 0.46605707540935915, 0.7661672376561073, 0.6628650623922373, 0.5301353389021559, 0.7444400981092264, 0.6309801429703986, 0.41803098836901326, 0.5225135014999098, 0.8075448558436736, 0.358663945032055, 0.5954270835729439, 0.5264028616718881, 0.42642196821380646, 0.7769476519995111, 0.5364367148508581, 0.4397493840790682, 0.7136220223637799, 0.41051722323586687}, {0.4232487699167587, 0.6181857943015244, 0.8743103641360651, 0.3170575948914392, 0.5916531205485835, 0.6608495742025406, 0.8082067418322458, 0.7305254197987352, 0.4701671495769721, 0.546451222093734, 0.37715245967291583, 0.7318089625377923, 0.4267897113881844, 0.7991219642344326, 0.565033193705126, 0.6426223435707324, 0.48313294725335637, 0.5946055436237909, 0.765151870253777, 0.5329559119348615, 0.8585136079001638, 0.41568343321649553, 0.5275258716885587, 0.3916178694554275, 0.675231153526162, 0.5510981872288385, 0.47656724873261863, 0.4758343336540671, 0.45338260068691766, 0.4316533166248422, 0.4468002267729337, 0.6947019021130854, 0.6114686523172694, 0.43923397786176593, 0.5904971638349714, 0.5552738288075431, 0.7487592882691894, 0.7055414693104677, 0.5542223878786555, 0.4608649964652265, 0.3301161899603931, 0.47656724873261863, 0.4669608358205907, 0.5669836688067782, 0.4699129108440837, 0.6748163838096475, 0.533273910131617, 0.7281334416779163, 0.41568343321649553, 0.6383473277179491, 0.38900532870581783, 0.41389019509339803, 0.7675585964844439, 0.765151870253777, 0.4217464235673058, 0.5556672700147365, 0.43142406758109203, 0.41796068885227056, 0.3301161899603931, 0.5737782371204483, 0.4877168283169193, 0.35933035611336933, 0.4386564148917164, 0.6467930961996038, 0.6576851459251881, 0.4828075342872227, 0.42567854057997595, 0.7281334416779163, 0.6353850949342196, 0.473827186770082, 0.6142871820715473}, {0.3426305087855256, 0.49377722343125674, 0.41322273293973055, 0.2281626952614667, 0.4036457364683678, 0.3886462464782513, 0.4967889602092665, 0.5252974170048037, 0.3617573766370015, 0.5399804205318351, 0.3828222967464987, 0.47071398303151635, 0.4035292770585552, 0.45004180340787936, 0.369587678074538, 0.5499787781701291, 0.5896515317504031, 0.49415121897014047, 0.46077022284706765, 0.6863007591017811, 0.4041359470919137, 0.2951989921419213, 0.5285919407526953, 0.2695749175373968, 0.3823663431874897, 0.7134807280598261, 0.48404376181350095, 0.5108837277359785, 0.40053014713958024, 0.3701902903896338, 0.5802198160459822, 0.36867349791946363, 0.7276924007973665, 0.37014017384634684, 0.7374662295570235, 0.49882013713882856, 0.5532561793279392, 0.6125072440003665, 0.45492300621858245, 0.6533760122528203, 0.24577620224093302, 0.48404376181350095, 0.4402141910248043, 0.5485533857102295, 0.33151078569145487, 0.4737078604971089, 0.441695204031297, 0.5821949149950548, 0.2951989921419213, 0.5527271026554124, 0.3592206565304626, 0.31448784041367467, 0.5115923237839833, 0.46077022284706765, 0.3318454867618211, 0.47701271281055563, 0.5673530294652367, 0.32287901690610676, 0.24577620224093302, 0.40468168885131045, 0.7157400020430895, 0.3407184623901009, 0.29881505989452406, 0.5211439158808343, 0.676250077471821, 0.45246008163577545, 0.43382552580572764, 0.5821949149950548, 0.4445334875403462, 0.5896515317504031, 0.3669102436856788, 0.3567520898354639}, {0.37619510588807437, 0.6005036161345563, 0.8359431819668753, 0.24892513279280218, 0.566388329358228, 0.6421307664540971, 0.8471857809886089, 0.7408162555157012, 0.4265840505322998, 0.5095738914572445, 0.3096047333861695, 0.7541537694118419, 0.37869205966836555, 0.8490178244608944, 0.530991374183557, 0.5567091366700616, 0.4331394262006903, 0.5708193285164469, 0.8019276902630291, 0.4857724293477699, 0.726617644849219, 0.36665899542529423, 0.48780557226532506, 0.3409644355345735, 0.6536820721714515, 0.5050812981612839, 0.4309180955766184, 0.39956308911200766, 0.40802672706331083, 0.3510742652750521, 0.3912465864204131, 0.6590618011579041, 0.568908569443897, 0.3931339689651981, 0.5468981727549411, 0.4674831976856047, 0.6845051154679013, 0.6581161308143832, 0.4614404120142156, 0.40121002972118336, 0.26042035903971644, 0.4309180955766184, 0.42190177004521856, 0.5336107844106042, 0.4246144519019864, 0.5720527855811335, 0.4982200761422527, 0.6739751093816586, 0.36665899542529423, 0.6205606284123284, 0.3164492361124422, 0.3315841377163416, 0.791299037692595, 0.8019276902630291, 0.33946923670404733, 0.4653415098301241, 0.3724262672970703, 0.335604426783485, 0.26042035903971644, 0.5454323798628687, 0.43138835338015824, 0.2919272275478126, 0.3895523698695533, 0.5554838911128034, 0.615571737592527, 0.43939752921464603, 0.3520743978439191, 0.6739751093816586, 0.6233994831359845, 0.4331394262006903, 0.3846007191970293, 0.5692522315471882}, {0.3278223992547205, 0.4749259795661652, 0.402013472340464, 0.22303123769227412, 0.3882230139779047, 0.3751528011581195, 0.48354363596791416, 0.5095954524722685, 0.3464737248800919, 0.5167915370622554, 0.3783458967610174, 0.49720715243422303, 0.3858665806562591, 0.4367011524137649, 0.3554117959964392, 0.5490374995668965, 0.5640448054831246, 0.47459066873335126, 0.446587508666162, 0.6553566863779451, 0.39477153983230107, 0.2828356735392747, 0.50551595274378, 0.2583453520604319, 0.36940453349677244, 0.6817661974367718, 0.46264675809945355, 0.5124597256263144, 0.383043303750699, 0.36666158085995926, 0.5598787319888673, 0.35671525792871783, 0.7052652926677939, 0.354066680630365, 0.7100578315857662, 0.4988909384786151, 0.5459008685384353, 0.6036671800177766, 0.4528986166760938, 0.6398280851068501, 0.24066735421539626, 0.46264675809945355, 0.4208658440645729, 0.525600387903061, 0.31786978198496074, 0.4692785552072689, 0.4232144630714469, 0.5743198216334088, 0.2828356735392747, 0.532625144195953, 0.36085353335597187, 0.30974707141809593, 0.49720715243422303, 0.446587508666162, 0.3274227843246634, 0.47597853272008894, 0.5533271840265565, 0.31828442848687305, 0.24066735421539626, 0.38887325628024255, 0.6921049930247218, 0.3359268128766938, 0.2865565800676606, 0.519064042704771, 0.663086434467686, 0.43262196816354764, 0.43171696381239444, 0.5743198216334088, 0.4281277092905224, 0.5640448054831246, 0.3628889711293755, 0.34805507700907357}, {0.362654200182266, 0.5759157635604114, 0.5873514997282473, 0.26010004856837327, 0.4885223281692248, 0.5034827142140288, 0.7149581291503194, 0.7074138245721023, 0.3984239542616701, 0.541068100623015, 0.36112529910446073, 0.7162739965283783, 0.39126573008004417, 0.6251227213433074, 0.4494478142628255, 0.7052845883930203, 0.49428084562530494, 0.5577967375151066, 0.6283451527990755, 0.5724098947002061, 0.5808940864850438, 0.3333271373303033, 0.5185486629914845, 0.3075176471592876, 0.5018653803268373, 0.6033378620717657, 0.4578478639551824, 0.4895062089352813, 0.407843014324753, 0.40006216325382454, 0.4574007655693165, 0.4932675522376099, 0.7190856208407889, 0.3851265292063908, 0.6763722797479989, 0.5677848412434776, 0.8700925960200023, 0.9711494535057644, 0.5391265242001796, 0.4864560334279455, 0.2764119942102646, 0.4578478639551824, 0.43394292521746763, 0.5651176596147084, 0.3803135643362204, 0.6448787228978015, 0.48106776463028106, 0.9439034605555147, 0.3333271373303033, 0.6425072141119157, 0.3650276728359458, 0.35972627276901376, 0.7162739965283783, 0.6283451527990755, 0.3741391412068103, 0.5549580524081509, 0.44230915250584224, 0.36687995025216097, 0.2764119942102646, 0.48048296525038037, 0.5229249409184683, 0.3346885542047073, 0.34574873527709576, 0.6792640513120823, 0.8269171361848278, 0.4506139275259774, 0.41837087473249257, 0.9439034605555147, 0.545618028575154, 0.49428084562530494, 0.42622797903780363, 0.45736712425003695}, {0.7549618533866541, 0.6053935335899545, 0.5367373221519703, 0.33043973485762324, 0.644251001896887, 0.6041167532081541, 0.5406172122227446, 0.5838550114512228, 0.7362057144100222, 0.5998325048846425, 0.40013794876446535, 0.5541008447746371, 0.6441372383566314, 0.5686314483931202, 0.6613546313990792, 0.4770294087410859, 0.5590919437251046, 0.6119425698009924, 0.5686314483931202, 0.5518781866367163, 0.5056777556161421, 0.8503356787768312, 0.6038597329909815, 0.8677672261037539, 0.5938215963844042, 0.5487219338313123, 0.6124085721172765, 0.43843967497116515, 0.6786998045092851, 0.39899002463348315, 0.5215361751993041, 0.5733573280102479, 0.5373266350928011, 0.7141291218304382, 0.5413348749895995, 0.44928655817342633, 0.5080707226862365, 0.5184467509821271, 0.44157460439268803, 0.5192557450342591, 0.7145057968491144, 0.33968229542532874, 0.6124085721172765, 0.6429190677321085, 0.5953353897693925, 0.7572579424579984, 0.4705080236017986, 0.6528956748193925, 0.5132969904404797, 0.8503356787768312, 0.5793817774479721, 0.39172328984208377, 0.38169992474905795, 0.5541008447746371, 0.5686314483931202, 0.3875729960636465, 0.4456417058753631, 0.49184856798971754, 0.38460398989074074, 0.33968229542532874, 0.6541889483125894, 0.5173902806615719, 0.38213715282769395, 0.7914240072376832, 0.47274857093836664, 0.528235727559802, 0.640521179741566, 0.44096733778607, 0.4815723397809485, 0.6068399277807605, 0.5590919437251046, 0.4077383146043815, 0.6265177574673194}, {0.8485753728091002, 0.692690467156322, 0.6141360957233121, 0.3919837843283584, 0.7282079229662431, 0.6827580983375274, 0.6211141893450881, 0.6677158130365471, 0.8299513357867017, 0.689524116424261, 0.4721819900463055, 0.636577702165858, 0.7348937902529492, 0.6502493665501842, 0.7413018891571017, 0.5552122507818801, 0.6464950119683712, 0.7003432216604472, 0.6502493665501842, 0.6386124837768337, 0.5814746366645354, 0.9127923904281446, 0.6941064074313322, 0.8773091730866356, 0.6712821529114666, 0.6350392062481381, 0.7033429033072937, 0.5143736695701325, 0.772850818201923, 0.4699363904470683, 0.6057645298064569, 0.6487400271887973, 0.6219574280034049, 0.8093996181820848, 0.6265811629444145, 0.52528712442277, 0.5881590573728631, 0.6001290933913992, 0.5163662132913422, 0.6034928877268164, 0.808594381222296, 0.40277080304059365, 0.7033429033072937, 0.735502229027037, 0.6843438280315156, 0.8375575220598438, 0.5470088573057946, 0.7443312862294853, 0.5941890435825489, 0.9127923904281446, 0.6658254155360328, 0.46250442643197487, 0.4498506812393355, 0.636577702165858, 0.6502493665501842, 0.4566719432475796, 0.5210701358863037, 0.5734937545612873, 0.4532232460012051, 0.40277080304059365, 0.7396855027137246, 0.6014823587769806, 0.4519313915304169, 0.8490595060458354, 0.5447038278813944, 0.6114491980057618, 0.733133634412578, 0.5172873892354564, 0.5604440318514513, 0.690523161757673, 0.6464950119683712, 0.4788121008692972, 0.70262888494615}, {0.7876069462569846, 0.8179504527362378, 0.751395028393113, 0.4401968503984082, 0.8976798962766623, 0.840832611586017, 0.7431250397756758, 0.8029202738668949, 0.8629968261856205, 0.7755832055550685, 0.5189757798630088, 0.7592319228186283, 0.7379068821462819, 0.7852787740408822, 0.9291061532391002, 0.6434164378832853, 0.700312904451692, 0.8188020517419314, 0.7852787740408822, 0.7113279451858706, 0.6939688109188252, 0.8416280553614137, 0.7710655017074176, 0.7980051098669343, 0.8240476409017821, 0.7131931132840795, 0.749860219147179, 0.5783898011720858, 0.792283596052051, 0.5309094084388651, 0.6498729036740528, 0.7906097545092241, 0.713242872733309, 0.8035041918899184, 0.7140846080669689, 0.6059955110605103, 0.690359818968125, 0.7009019135542738, 0.5939702061339809, 0.6575273122250814, 0.7860639226898861, 0.45200822191194506, 0.749860219147179, 0.7725614682244397, 0.7788410847152554, 0.9298943740439702, 0.6385838312029772, 0.847712553407957, 0.6958848069285938, 0.8416280553614137, 0.7760893923089165, 0.5142271196846823, 0.5144155945067782, 0.7592319228186283, 0.7852787740408822, 0.5179965794466201, 0.5985101088252205, 0.6175165995783194, 0.5144155945067782, 0.45200822191194506, 0.9055377780991716, 0.6603325148148852, 0.49786208553409234, 0.8753126895190148, 0.6332468740414552, 0.7086853163463305, 0.7837026741921557, 0.5799990587880117, 0.6481601354249152, 0.8404453596812559, 0.700312904451692, 0.5473281095437835, 0.8687426020631371}, {0.3191714535334274, 0.44692274738093213, 0.45735500276050756, 0.3195311828004395, 0.3908373842150935, 0.39679567375520713, 0.502337754618372, 0.4594208145778416, 0.3390899636025745, 0.452242938410534, 0.4999908367919248, 0.5033759556444997, 0.3616651304915679, 0.4623125085160759, 0.36663887601204886, 0.7337614836833237, 0.4672499015153235, 0.4410696294784311, 0.4623125085160759, 0.5149703733216772, 0.4730065263306069, 0.29526086913528726, 0.44241721010387786, 0.27556899984076894, 0.39640886749964566, 0.5310279533296792, 0.41161279599592066, 0.7558042625674805, 0.3627604018364356, 0.5572135970916972, 0.47694740662514656, 0.3939994677858608, 0.5768373692219331, 0.3432651805751978, 0.5625940294857587, 0.8504021933277586, 0.6107672514153671, 0.6133878915626039, 0.7014539526485994, 0.5175821621520622, 0.3369683924028351, 0.34712281663153627, 0.41161279599592066, 0.38676347750357415, 0.46174377406061057, 0.3264631267872819, 0.6338509501797067, 0.3993037805760767, 0.6142012089554112, 0.29526086913528726, 0.4928168097404731, 0.5139820819236228, 0.47651948299893776, 0.5033759556444997, 0.4623125085160759, 0.49224376331141073, 0.7475720715120383, 0.4984369490730692, 0.47651948299893776, 0.34712281663153627, 0.3876960250199276, 0.5406734212528632, 0.45995655934320856, 0.30182783580709693, 0.679538338897599, 0.6010525249527247, 0.3956394256825239, 0.7402197579212453, 0.7513041383201047, 0.42596015148658456, 0.4672499015153235, 0.5527054682090861, 0.37012558989194766}, {0.6311427480096607, 0.48182061839073886, 0.40785677762932915, 0.19784814635069814, 0.5448776799089209, 0.48659203718269384, 0.4024555531628309, 0.4563438221913845, 0.679387912792622, 0.4644777268374262, 0.2514757780342894, 0.4179945051973903, 0.49038702808917123, 0.4376199198237233, 0.5737908709091968, 0.3291830851993834, 0.4054848651922246, 0.4889363585531391, 0.4376199198237233, 0.40315076085628043, 0.3626363839142286, 0.9871629636189392, 0.4669141073126134, 0.797543637946366, 0.472100068415038, 0.40114368656241756, 0.46747488252490665, 0.2880920632854047, 0.5509691368554485, 0.2537097054127288, 0.36180003654028264, 0.4441919816803169, 0.3919585761477345, 0.5971884850954053, 0.3954186412404487, 0.30391818850835195, 0.36326163072949674, 0.3738377707576727, 0.29386185512196566, 0.36253442376507145, 0.5857626630373033, 0.20488469350248806, 0.46747488252490665, 0.5051535852496285, 0.46112904379509956, 0.7532025352523598, 0.3235806169234984, 0.5434575541412434, 0.3686103807117103, 0.9871629636189392, 0.4395745265551051, 0.24577653405345054, 0.24200035335949657, 0.4179945051973903, 0.4376199198237233, 0.24443198429105, 0.29750479920536277, 0.3321371331193576, 0.24200035335949657, 0.20488469350248806, 0.5592741257439152, 0.36203395032842617, 0.23679310473263174, 0.8089646399456145, 0.32051250548411436, 0.38349915730920403, 0.5073293904979671, 0.29002868430564455, 0.33349441629002397, 0.4888513459585787, 0.4054848651922246, 0.26253034611415094, 0.5184956435579804}, {0.3812059763321668, 0.5682299959189029, 0.5943949356703632, 0.18645701856209712, 0.7102296061839947, 0.893192953302149, 0.5463224623582978, 0.6230272866121696, 0.45715778681274927, 0.45875607709176036, 0.23133112126809227, 0.5529485207840537, 0.3620790305850839, 0.6332853345043892, 0.7344262169689265, 0.37008618887260986, 0.36607651290632065, 0.5458128938944887, 0.6332853345043892, 0.3952036124337308, 0.4793249080532042, 0.4230780486877882, 0.4429272903143415, 0.3903473458578349, 0.8483221425226156, 0.4041338816027664, 0.3969183915270656, 0.2866250989024299, 0.4102309673560771, 0.2516465024269781, 0.3197339662734885, 0.7275271461395919, 0.4267492854258285, 0.4074627757999717, 0.42009047872976235, 0.3225042798261515, 0.4376659133664743, 0.4405461215079592, 0.3156871201722382, 0.33391147487293527, 0.38917733078982, 0.19371291160668108, 0.3969183915270656, 0.4064791612375209, 0.47503110138907517, 0.514987026283688, 0.3740593828699115, 0.5176201021884048, 0.43981922338998286, 0.4230780486877882, 0.5333846361700622, 0.2323211799927092, 0.24151889298693283, 0.5529485207840537, 0.6332853345043892, 0.24388044510659057, 0.3185974434666418, 0.29592700084027024, 0.24151889298693283, 0.19371291160668108, 0.6627219200062275, 0.340700867058546, 0.21767542551903568, 0.46990454177103724, 0.3635480059321463, 0.4366951566777971, 0.42424309199313776, 0.286295631199828, 0.37251466123150867, 0.6997742171506145, 0.36607651290632065, 0.2711189226760157, 0.8925586789620057}, {0.35740998936038304, 0.48947993845208565, 0.5012537862964032, 0.35949445423389437, 0.43259993732863095, 0.4391025196958276, 0.6482368000715257, 0.5460332583276729, 0.5024960282333537, 0.3784741900738697, 0.49428321468549047, 0.528918278373411, 0.5424783758877828, 0.5466017871017421, 0.3933735247155861, 0.5057331731731359, 0.40765198679694303, 0.7691175563931443, 0.5086915891804278, 0.48345212207287214, 0.5057331731731359, 0.5560593483089793, 0.5176797448132884, 0.3323404420891568, 0.4843132112137243, 0.31134536044541067, 0.4380567656057959, 0.5718722264349307, 0.5086915891804278, 0.7850604843717809, 0.4029145364524771, 0.6017175757622021, 0.5180195729477237, 0.43652109504606923, 0.6168016675728356, 0.3826792313566674, 0.6028376493618948, 0.8784483395666149, 0.6519364365827627, 0.6534147699177987, 0.7417527417223819, 0.5580446936943014, 0.3760586338333011, 0.3886042807726126, 0.4529305210247872, 0.4276127101114619, 0.5129908224245593, 0.3654241632845653, 0.6763295788049825, 0.44084732907372504, 0.6547068994261086, 0.3323404420891568, 0.535490255205366, 0.5574808505933644, 0.5221240946557917, 0.6357721408167922, 0.5466017871017421, 0.5057331731731359, 0.5378682143401995, 0.7847642868545168, 0.5359120540288193, 0.5221240946557917, 0.3886042807726126, 0.42928086745189575, 0.5805416516041033, 0.358816114484569, 0.5031569910620707, 0.3394214336497932, 0.7198199657937678, 0.6299125823500877, 0.43676963059630747, 0.7697208095500627, 0.7840816734294247, 0.46864526473785034, 0.5086915891804278, 0.5983514543054516, 0.41149476845635885}, {0.2147792911457706, 0.27029688589983686, 0.24675030957335728, 0.19804247132832667, 0.23324202824491178, 0.22656962881081416, 0.5571409494276457, 0.3548945880388057, 0.27024736465792126, 0.2639750015711823, 0.2195745120378375, 0.2959761666363753, 0.2915691168798989, 0.4401512667467807, 0.27876785624088835, 0.2559466622615837, 0.25800499407493177, 0.21914797761441066, 0.34361454196172253, 0.3503780642057843, 0.2536522729754536, 0.2611507063222488, 0.35649700586941196, 0.24555450491760092, 0.1866030837672216, 0.2959761666363753, 0.17274319757990653, 0.2216369268289687, 0.3561100913644545, 0.3503780642057843, 0.4931739898855344, 0.24924652611111778, 0.3446224996895836, 0.4129597876245431, 0.21912360649961957, 0.348266645529231, 0.23102681306399855, 0.351956909930754, 0.3845376592126173, 0.3111834028623301, 0.3253319817676521, 0.33789493437269547, 0.4283194817862781, 0.22924356146554475, 0.21937472574769976, 0.29034851604187356, 0.29034851604187356, 0.29498797595564835, 0.3446224996895836, 0.20300137148891084, 0.30432682459827465, 0.25414666529829166, 0.3253319817676521, 0.1861578582542262, 0.2890696462632192, 0.41282015283777806, 0.2804439394452192, 0.2957930791299359, 0.31847860151335633, 0.25800499407493177, 0.2913213699597238, 0.3532251444314019, 0.5035279796672139, 0.2804439394452192, 0.21937472574769976, 0.2342198576058255, 0.43479657363718055, 0.20274098873546678, 0.4401512667467807, 0.18669276295220474, 0.32071713186070305, 0.3435580540856532, 0.2695785305006724, 0.5255973858407257, 0.3548945880388057, 0.24817551690032372, 0.3503780642057843, 0.3007973811589534, 0.21407941712102196}, {0.3209965067211806, 0.4660965720540828, 0.41750025666429624, 0.12014285219069544, 0.9388806481409422, 0.6163921467417789, 0.23046316687729212, 0.2651702588890789, 0.40928585623639635, 0.5525125340094024, 0.4140101962483412, 0.4149816228776714, 0.4660965720540828, 0.1489735825298199, 0.4423697367421587, 0.2900536349652535, 0.5036188302535951, 0.7083597701249285, 0.2651702588890789, 0.3003383101387437, 0.6119632054581705, 0.5303528801509351, 0.32615082722000976, 0.32232296659850035, 0.3410079970823562, 0.4149816228776714, 0.2973201652413933, 0.5021700760822617, 0.33293945952329823, 0.3003383101387437, 0.20292303316276977, 0.36296434152981216, 0.17043097658298187, 0.24952947748109092, 0.46259208244084593, 0.3448492262982431, 0.35675879908488056, 0.3424422360885418, 0.22776964152428072, 0.3230166351542618, 0.3363593796894375, 0.2179358299709692, 0.26123482247069474, 0.3341464242839689, 0.12587819648988868, 0.34362083010637906, 0.34362083010637906, 0.44693777336101914, 0.17043097658298187, 0.47695779581949416, 0.2604725028183864, 0.5633451003683159, 0.3363593796894375, 0.32786965905374515, 0.4678854409186212, 0.15889420324426382, 0.16026078243143393, 0.30556319803193577, 0.33017265695070946, 0.5036188302535951, 0.1624603254845196, 0.22486522738172104, 0.22136792372744127, 0.16026078243143393, 0.12587819648988868, 0.8084246061280287, 0.27060605748692323, 0.40762161863480023, 0.1489735825298199, 0.37632021050442754, 0.2548970047503826, 0.34541360533115223, 0.3807174528505102, 0.20369169410576324, 0.26988569720446925, 0.6891733222070695, 0.3003383101387437, 0.18190939253465627, 0.6040867758218308}, {0.32045373919666464, 0.44785495425964883, 0.4580362843425033, 0.44824053662012886, 0.3860281250368331, 0.39943043624064645, 0.505621577887827, 0.6455592177254852, 0.48326872308087776, 0.43870650601561706, 0.3383391104027337, 0.4182106897974701, 0.44785495425964883, 0.5248523280136393, 0.473565151427766, 0.34519361228233375, 0.44792226054323453, 0.365780619728035, 0.37966070910278893, 0.6455592177254852, 0.4244301191582372, 0.39507626987893013, 0.443560107510813, 0.45537033508242397, 0.49312369803682665, 0.3051703415325963, 0.4182106897974701, 0.2893447944910705, 0.4043018552371622, 0.46585560096922046, 0.4244301191582372, 0.6946375976104606, 0.3555779957981461, 0.8009159991846031, 0.4330559222961595, 0.40591509611274124, 0.49776305989905345, 0.3392925260712606, 0.48726564418070234, 0.7427007388582825, 0.555277308777538, 0.5386699393911891, 0.798574853744993, 0.4579186085613211, 0.3339648454573845, 0.48967512029876203, 0.3876706679871932, 0.3876706679871932, 0.43327117280052146, 0.8009159991846031, 0.33525771205006494, 0.6382494003718221, 0.3896989009379627, 0.5386699393911891, 0.3022394879761076, 0.454911221981057, 0.6279334206846791, 0.7068529749511657, 0.5675885098065597, 0.5475131595985493, 0.443560107510813, 0.7329928779406844, 0.7661993281643988, 0.45126887347524125, 0.7068529749511657, 0.48967512029876203, 0.38190362913838194, 0.4844366480445768, 0.32537883203753803, 0.5248523280136393, 0.3128123303388102, 0.6633662346069709, 0.5086198301311784, 0.3775199204083246, 0.6648785529118906, 0.632354647311277, 0.4144441416059738, 0.4244301191582372, 0.368983313922342, 0.827028718043951, 0.3748230322195703}, {0.4158050129378983, 0.681070407881156, 0.5843502815229651, 0.18958558436205428, 0.7925261819484235, 0.7035608515463353, 0.35398873487712307, 0.40685918781418423, 0.5966616533897208, 0.7969718006943579, 0.49925589650935454, 0.49579300035536367, 0.681070407881156, 0.23468924660963347, 0.6506697835536913, 0.6893282611571897, 0.4019766091725015, 0.7104150902895443, 0.6657773702551901, 0.6149885489676008, 0.40685918781418423, 0.4412380706429262, 0.7453614156398929, 0.7558683631743869, 0.5561125334458674, 0.46782958894183274, 0.41523302050099625, 0.5835008212559392, 0.37183681925439227, 0.6176150861081393, 0.49579300035536367, 0.4412380706429262, 0.31630647308605936, 0.4782680987184991, 0.26713860034333714, 0.375109952595566, 0.5826729530972683, 0.5196061900093178, 0.4595232670630329, 0.5141825360351379, 0.3528623824411267, 0.4998663369791547, 0.5095929271299706, 0.3373644814681722, 0.39417713390728343, 0.4366887851014865, 0.19868498908861743, 0.48006156466318645, 0.48006156466318645, 0.6388377835362865, 0.26713860034333714, 0.5240327845830672, 0.39736658368968086, 0.6906800631879763, 0.5095929271299706, 0.40441607661467166, 0.6893282611571897, 0.24998477781991704, 0.25103565640550357, 0.460750096576317, 0.4998663369791547, 0.7558683631743869, 0.25451946096838907, 0.34830224333372223, 0.33790057391098044, 0.25103565640550357, 0.19868498908861743, 0.7619472580045973, 0.41019787943406244, 0.4777697406922963, 0.23468924660963347, 0.44236712808098566, 0.3905832744332523, 0.5219476942886706, 0.5124987934017412, 0.31737884597706634, 0.4142425023079964, 0.9250921327516967, 0.4412380706429262, 0.6736521269550587, 0.2834183692349885, 0.6597068424291475}, {0.35584971503887114, 0.736897147637884, 0.43880317861601487, 0.1434561320672456, 0.592371412737192, 0.49062586993050933, 0.3145359403361871, 0.338796013663469, 0.4791041494196925, 0.6600014328726226, 0.4282806953657327, 0.49460121148853636, 0.736897147637884, 0.18928309139960392, 0.5527462371889358, 0.6944080602150879, 0.36552400353251846, 0.5516438664651198, 0.5018393414629483, 0.4302144852602149, 0.338796013663469, 0.4293590665844753, 0.7465496590477547, 0.6010004828264238, 0.5954545630585125, 0.3540409857530372, 0.3246994629571149, 0.6433245617597014, 0.28370048389690866, 0.43460178465841703, 0.49460121148853636, 0.4293590665844753, 0.26352295349410126, 0.46948057343943655, 0.2123742821947101, 0.3451821948929685, 0.41155369534226505, 0.5023611358145124, 0.40973132382688665, 0.5050988635235756, 0.29185018024475756, 0.4298402440000171, 0.4482277722170188, 0.2720257072079709, 0.36611076724291264, 0.38654576620952946, 0.15161867856266883, 0.47561124660208354, 0.47561124660208354, 0.735550034737526, 0.2123742821947101, 0.40714566614431286, 0.3196327212058244, 0.7008931433921208, 0.4482277722170188, 0.3181535434908048, 0.6944080602150879, 0.200957271544216, 0.19537484041385228, 0.37462997423170763, 0.4298402440000171, 0.6010004828264238, 0.19887897663701443, 0.28561456863008833, 0.30068746162206583, 0.19537484041385228, 0.15161867856266883, 0.6027637386291387, 0.3818364785982032, 0.3848275130848457, 0.18928309139960392, 0.33767924056814547, 0.317486852261093, 0.4939810384902088, 0.5075821531409563, 0.26590434283180475, 0.3494926466649677, 0.6502863694154465, 0.4293590665844753, 0.49424878393687954, 0.22267575828723457, 0.46678843944521853}, {0.5290217186543614, 0.6315140027895616, 0.43902722090797264, 0.20169723601754871, 0.5570479953778995, 0.47545678974176936, 0.44799134623829234, 0.40202211003797955, 0.46959982243435655, 0.5529844194471251, 0.5853630698212671, 0.6289793229095573, 0.6315140027895616, 0.27544068227595625, 0.5135526774671417, 0.5971908457619114, 0.6266154103019066, 0.5022495018235917, 0.5244875689253984, 0.444427756308667, 0.40202211003797955, 0.6617789052807688, 0.6733678452489227, 0.5264274735255321, 0.8658039866166016, 0.3851805939116153, 0.4390170845106511, 0.8118780860162734, 0.38717860040280955, 0.4383423445129621, 0.6289793229095573, 0.6617789052807688, 0.3544701635679137, 0.8096375225138416, 0.2877394954130669, 0.5309907260088822, 0.4227946534271372, 0.5659630293776468, 0.6292494864184809, 0.6089032566909531, 0.3688763994754955, 0.4643607190242678, 0.48261007605562684, 0.3424619690268991, 0.5372496872548663, 0.6073174778572453, 0.2129763769263661, 0.8365093830841531, 0.8365093830841531, 0.7120712720170259, 0.2877394954130669, 0.4918940328803504, 0.3756346366821114, 0.7070675710672112, 0.48261007605562684, 0.43802697236315985, 0.5971908457619114, 0.2834665477585914, 0.2636007189057914, 0.41445994779125395, 0.4643607190242678, 0.5264274735255321, 0.26835622945470394, 0.3601313688136933, 0.4446175361593947, 0.2636007189057914, 0.2129763769263661, 0.57893872245206, 0.530568718742875, 0.5042088152022741, 0.27544068227595625, 0.43298765012062523, 0.37804648837024235, 0.5436873966333614, 0.8686624727460158, 0.36019090224183636, 0.4147331387914295, 0.5524378182139049, 0.6617789052807688, 0.5087458357941875, 0.29192761909986825, 0.4763399581550717}, {0.5473458112882321, 0.9141795935088547, 0.6465545718698122, 0.29306767820152774, 0.7345159582739398, 0.6710906697122537, 0.5496755246465315, 0.5703257868456437, 0.6618802541468216, 0.806841026527933, 0.6102419586173663, 0.7446215819596755, 0.9141795935088547, 0.3719329951468604, 0.7549558423170849, 0.8713740356946078, 0.5865633570438014, 0.7703938787525261, 0.6723341241455639, 0.6195990870295213, 0.5703257868456437, 0.6744564039055966, 0.8277657982355502, 0.7703938787525261, 0.8076895686653671, 0.5694439202282265, 0.5138067618946353, 0.8477678499772553, 0.4739895073675262, 0.6288664502533009, 0.7446215819596755, 0.6744564039055966, 0.4805973463340222, 0.6860572624532685, 0.410217485160365, 0.5846052888071246, 0.6097137029412739, 0.749107605143315, 0.6172175513189323, 0.7523228451207188, 0.5154466128089297, 0.6653254292699319, 0.6857873851843276, 0.4874818692420149, 0.6105180649903443, 0.5964634366571255, 0.3074291073013508, 0.7025756442162397, 0.7025756442162397, 0.9306196315826285, 0.40534155091447477, 0.597500099466459, 0.5435346359775015, 0.8130727631860157, 0.6857873851843276, 0.5138067618946353, 0.8713740356946078, 0.39174988854966486, 0.3780980084319696, 0.6031902160216648, 0.6653254292699319, 0.7703938787525261, 0.3837429871044229, 0.5066686850159469, 0.5152759866618668, 0.3780980084319696, 0.3074291073013508, 0.7408690559735814, 0.6293956269189568, 0.5804757248199619, 0.3719329951468604, 0.5315494981071621, 0.5428344819868391, 0.7384088505099853, 0.7179552696438525, 0.48426405109751963, 0.5839564350194308, 0.7736367084209338, 0.6744564039055966, 0.6667880108147657, 0.4186488853416295, 0.6472466790094653}, {0.37662554059445297, 0.516591413876232, 0.3731739414818417, 0.20435447929117656, 0.3990490692870129, 0.366203427921529, 0.6215863143825022, 0.4221190256263488, 0.3961272409591673, 0.44061201823961305, 0.39038336451729905, 0.7030529283159874, 0.516591413876232, 0.3164638564258059, 0.4438700492771493, 0.4976674886026926, 0.48010489742332296, 0.42669813091519226, 0.3722890040305684, 0.34005882988982, 0.4221190256263488, 0.8139576682928652, 0.45758094728647464, 0.42669813091519226, 0.5939876056750754, 0.3450459611405973, 0.31381065322509916, 0.5839694594510441, 0.28409111443030793, 0.3476777401934686, 0.7030529283159874, 0.8139576682928652, 0.4155777632562216, 0.5231346111379891, 0.3212094811946883, 0.7415978763579412, 0.33923940768863003, 0.5898179720545503, 0.4646099379688193, 0.6616750066902429, 0.40648351940656224, 0.45744376991209984, 0.47895254315353053, 0.3635058714368623, 0.8527836695267337, 0.4174416131652993, 0.2190661006969264, 0.5936856199138675, 0.5936856199138675, 0.5534831377783094, 0.31328329479587724, 0.34515183876511646, 0.37735619608331866, 0.4650882467017359, 0.47895254315353053, 0.31381065322509916, 0.47898901104591596, 0.32649992113312687, 0.27631636565820183, 0.4012043368865454, 0.35051717231194407, 0.45744376991209984, 0.42669813091519226, 0.2832165414622033, 0.39154447750608246, 0.6150068750830829, 0.27631636565820183, 0.2190661006969264, 0.4060515904497629, 0.7689210381238782, 0.34954024342672707, 0.3164638564258059, 0.31118588457514296, 0.3881373043816727, 0.5576315150684277, 0.5282751339733924, 0.42858425041464454, 0.4410742714671598, 0.4410742714671598, 0.4149745811857247, 0.5936856199138675, 0.36673203983784103, 0.3061984497001636, 0.3541189032862252}, {0.4312025999455104, 0.5413380196879632, 0.4826893475989728, 0.3870610386882525, 0.4604993151953556, 0.4493050645923722, 0.8200031249915829, 0.5924569865952802, 0.5017304027089343, 0.5032061897331866, 0.4399652892479673, 0.6224415947562946, 0.5374439624020847, 0.6394536651660457, 0.5218545005507365, 0.5374439624020847, 0.4895213154293124, 0.4987808520430976, 0.43952642753835613, 0.4276235093828296, 0.5924569865952802, 0.6172701879185732, 0.4907369771566796, 0.4987808520430976, 0.54863669763099, 0.4722147331948392, 0.38805189922281247, 0.5489995766658377, 0.3654967472302493, 0.441175035889445, 0.6224415947562946, 0.6172701879185732, 0.7169165602509738, 0.5112828982901384, 0.5859088104495249, 0.6051937824964704, 0.43704552215232845, 0.6104523526210579, 0.4820993214251315, 0.6197895661026619, 0.6291299492720489, 0.5693253396047322, 0.578682369081772, 0.5769277992094812, 0.7028558943793944, 0.45600581119753475, 0.4165952327770656, 0.5422497889437224, 0.5422497889437224, 0.5468900331552163, 0.5698120078204896, 0.4149137129435403, 0.544920907200593, 0.4917160025778973, 0.578682369081772, 0.38805189922281247, 0.532715116238743, 0.6398628010713722, 0.49903828693009056, 0.5383813576346245, 0.5605930441549671, 0.5693253396047322, 0.4987808520430976, 0.5118198561730944, 0.6112868614977984, 0.7492083535941322, 0.49903828693009056, 0.4165952327770656, 0.4622248731083684, 0.708389056728235, 0.41482388874972576, 0.6394536651660457, 0.38865445095451623, 0.5639683055984859, 0.6037826206279355, 0.5141196182336188, 0.7433837469396058, 0.606402957751984, 0.606402957751984, 0.4786274539800835, 0.5422497889437224, 0.4378437356009055, 0.5274949367186793, 0.4334166851257563}, {0.41197531065897774, 0.5051062853388346, 0.4905464134087488, 0.50530131871041, 0.7049545651252493, 0.46035681223326463, 0.46855977653567743, 0.5663972624762886, 0.6074497697313139, 0.5246517501645686, 0.4962278911892218, 0.424539999170099, 0.525336941159883, 0.5085884252077061, 0.726223821129204, 0.5176376839239032, 0.5085884252077061, 0.43701597999263225, 0.4987923691527932, 0.4430397253641917, 0.43454888456359025, 0.6074497697313139, 0.5340523284132204, 0.4689892464969986, 0.4987923691527932, 0.48562224027362916, 0.5295424425590309, 0.3957344863927304, 0.48978621683351314, 0.38347200195950426, 0.4729994192954713, 0.525336941159883, 0.5038086392158694, 0.6804272682545263, 0.4578197849374226, 0.793095078954379, 0.49217313206809377, 0.4729994192954713, 0.4430397253641917, 0.5352896624481062, 0.44322308887599865, 0.4272253026317087, 0.5304119536193062, 0.6486797295409831, 0.5601189528152427, 0.5566203024558383, 0.6663169056707948, 0.5340523284132204, 0.4272253026317087, 0.7757384698754295, 0.4723439428884975, 0.4723439428884975, 0.497739406029359, 0.8000073864431689, 0.4221463777903102, 0.6026553373754122, 0.4689892464969986, 0.5528618407682816, 0.3957344863927304, 0.5117484456646102, 0.774784146073362, 0.8010735921144059, 0.5685069385346032, 0.6686654877380535, 0.5601189528152427, 0.4987923691527932, 0.8010735921144059, 0.6588717697054802, 0.5345699685223084, 0.7943991626997934, 0.7757384698754295, 0.45785409972248386, 0.5539291897476678, 0.39791494765310603, 0.726223821129204, 0.4034707033818384, 0.6134500216322266, 0.5398933915187055, 0.4620985303034431, 0.47361944300696995, 0.6707793841740236, 0.603306927858976, 0.6074497697313139, 0.46265890971061935, 0.4723439428884975, 0.4280917929301759, 0.7312362134141287, 0.4511277770984864}, {0.48562102877043445, 0.6731186523340226, 0.6256809522337687, 0.5815432544516739, 0.3767184289690762, 0.5505868449149003, 0.5359569501228527, 0.8203015808461687, 0.6949091893302908, 0.6059727400508893, 0.6155486938430219, 0.5067376921916963, 0.7905643439191858, 0.6680619407154136, 0.5355554932543585, 0.6421053843260246, 0.6680619407154136, 0.5434851342073945, 0.6085784538438819, 0.520674561564651, 0.47328590729368664, 0.6949091893302908, 0.8128436813474084, 0.6427006815382991, 0.5913502750879952, 0.6155486938430219, 0.664155516459548, 0.5551922491288647, 0.44106664290310343, 0.6705031832300675, 0.4135843094583993, 0.517196010695183, 0.9385757620902865, 0.7294158424347643, 0.7012576793838194, 0.5945876248230363, 0.5641477500316144, 0.6875396523893323, 0.517196010695183, 0.520674561564651, 0.7937731872243633, 0.5564668010312395, 0.5192262221830553, 0.7951549187539226, 0.7134645622261728, 0.701802852601846, 0.7211858916032421, 0.6254656496349341, 0.8128436813474084, 0.5192262221830553, 0.4012053767521657, 0.6349799685016815, 0.6349799685016815, 0.6765630396721886, 0.5523848564083946, 0.48241091901848043, 0.6287275350369183, 0.5913502750879952, 0.7403304176911538, 0.44106664290310343, 0.6607890669498415, 0.5613322819886529, 0.5059277619672984, 0.6427006815382991, 0.6057082575143471, 0.701802852601846, 0.6085784538438819, 0.5059277619672984, 0.6676781264824527, 0.7131744172061442, 0.4949474311294974, 0.4012053767521657, 0.5519740524053999, 0.915230144041216, 0.443151698843041, 0.5355554932543585, 0.4461806519685861, 0.6497167435036053, 0.7865263414733501, 0.603802622007354, 0.62328872628954, 0.7166694773716956, 0.7199665900266833, 0.6949091893302908, 0.548207440335903, 0.6349799685016815, 0.48169545891244825, 0.5413631482930518, 0.5159498045337189}, {0.248096790162941, 0.3479293602833282, 0.3321925619613874, 0.3524887330416569, 0.43614923299316527, 0.29996179398919715, 0.3100175512671981, 0.39716557286159965, 0.4950872992672599, 0.37661556082625297, 0.33996236885548636, 0.261244935483966, 0.3656102943001529, 0.35291367155485065, 0.4963476767681404, 0.3662179111232036, 0.35291367155485065, 0.26795388516381136, 0.3435071520444246, 0.2818663604507531, 0.27477959459307083, 0.4950872992672599, 0.365882314102814, 0.4368565893094973, 0.3074880607247943, 0.33996236885548636, 0.3215180595328429, 0.38303693576611403, 0.2361614830647395, 0.3269375800925071, 0.2260222086189775, 0.315348565083162, 0.4053954636542265, 0.33582605730375487, 0.5793374636323799, 0.29103010921367595, 0.8502278571969757, 0.32106780209614455, 0.315348565083162, 0.2818663604507531, 0.3806267415796238, 0.2773240699905943, 0.2620400731953965, 0.3731643179511309, 0.5576221791179836, 0.42205794921210676, 0.41592998058542774, 0.6075581764776654, 0.365882314102814, 0.2620400731953965, 0.49349532751996295, 0.30494432094901375, 0.30494432094901375, 0.337631607145895, 0.9129165859570657, 0.2612594845925485, 0.49050289872569497, 0.3074880607247943, 0.40941260260482176, 0.2361614830647395, 0.35753249836661594, 0.5998734589819098, 0.809440400699245, 0.4368565893094973, 0.6131892626013791, 0.42205794921210676, 0.3435071520444246, 0.809440400699245, 0.5862723018357702, 0.35432139840426363, 0.7585703276860073, 0.49349532751996295, 0.2969365172094212, 0.3951502326869066, 0.2383339461745037, 0.4963476767681404, 0.2440004909022543, 0.4930129635037583, 0.38789356698704075, 0.29605657770433336, 0.310101565693447, 0.5554868055311365, 0.48627243874827353, 0.4950872992672599, 0.3027702333758006, 0.30494432094901375, 0.26772297690709196, 0.7389645331433987, 0.2891507834328749}, {0.2971042897245078, 0.523787584571622, 0.48471666549039594, 0.5170729646282797, 0.2617370522511676, 0.40682320783591597, 0.42210820879793814, 0.45986161794118163, 0.8644451463764252, 0.5787662843565304, 0.5041907745022886, 0.32449499839222123, 0.5269766725683568, 0.5401040797869426, 0.339621036650767, 0.5764355930770494, 0.5401040797869426, 0.3205660752607232, 0.5112266344865211, 0.36818217022858146, 0.34683570775036277, 0.8644451463764252, 0.4614906294762553, 0.7307301637106888, 0.42333527584850716, 0.5041907745022886, 0.4400708330555745, 0.5289194858431863, 0.28157829149714797, 0.45613033094121497, 0.2646000573630199, 0.6123792321323089, 0.4209017364700652, 0.5656436074899912, 0.4378192673225951, 0.5321732474506735, 0.37234523125254143, 0.4350057278266419, 0.4015541377868425, 0.4209017364700652, 0.36818217022858146, 0.5834390896312884, 0.3492148370945278, 0.32126534517447775, 0.5542094820756455, 0.676731684502589, 0.506952923227195, 0.7751093279018013, 0.7534780266564728, 0.5011048088830495, 0.4614906294762553, 0.32126534517447775, 0.28063135575340087, 0.39495577730098763, 0.39495577730098763, 0.506952923227195, 0.4288862032822526, 0.32731545578797117, 0.7116957536072513, 0.42333527584850716, 0.7215762355536312, 0.28157829149714797, 0.5545204163014361, 0.3810659440123129, 0.39595145643753904, 0.7307301637106888, 0.5862043562742887, 0.7751093279018013, 0.5112266344865211, 0.39595145643753904, 0.6569556756586494, 0.39226048756475645, 0.3866370270190808, 0.28063135575340087, 0.4005406478650468, 0.5357595553148352, 0.28550162474853935, 0.339621036650767, 0.29542216900196855, 0.7634466648181742, 0.6146697143428589, 0.3848262906343374, 0.42204312650569403, 0.5247829468434817, 0.9104420311815756, 0.8644451463764252, 0.4121900379245727, 0.39495577730098763, 0.33844837368390174, 0.45967856396483725, 0.3809597284066832}, {0.07751823303949937, 0.15877856539358934, 0.14249073834628295, 0.07606434514829963, 0.022665338604670515, 0.10549068919580516, 0.0790172034728165, 0.0712644773885383, 0.06105685786438454, 0.07372344983175616, 0.1109695141759296, 0.09966996661534398, 0.15295548343228166, 0.1364620290552647, 0.03374930496408064, 0.09610721028879937, 0.1364620290552647, 0.1004608744869719, 0.09979583683764019, 0.09540868188502857, 0.062018886941160284, 0.06105685786438454, 0.09888589853286674, 0.06436967639837388, 0.1668359232331549, 0.1109695141759296, 0.6403686702170147, 0.056488035410313726, 0.0619529531434881, 0.3069861845100898, 0.05156849213137494, 0.04706586771643283, 0.06460159590514723, 0.09443005990231379, 0.155287839021514, 0.04920268249531218, 0.20176472331394787, 0.037110124704845794, 0.1320192711087276, 0.06460159590514723, 0.09540868188502857, 0.13088454979269637, 0.14663403473830283, 0.1054816473281787, 0.14224071217242243, 0.05480007959084349, 0.18944630921703798, 0.07924750528516154, 0.08543271407524868, 0.040559383179045054, 0.09888589853286674, 0.1054816473281787, 0.02427099430344369, 0.244728185770978, 0.244728185770978, 0.18944630921703798, 0.0362811607883397, 0.07769045659436943, 0.0545738616551079, 0.1668359232331549, 0.09249227636132902, 0.07847926190551315, 0.11974520075937532, 0.035807973652307985, 0.032975517914231485, 0.06436967639837388, 0.04525959573613638, 0.07924750528516154, 0.09979583683764019, 0.032975517914231485, 0.050856859203111116, 0.06784402457731518, 0.032185402577018234, 0.02427099430344369, 0.11323979487255668, 0.0971510889046668, 0.06247602615223549, 0.03377226612890497, 0.062051089494756205, 0.056811541140760195, 0.12007346625569272, 0.2502487762715528, 0.36324979312381994, 0.05036151652983978, 0.06438499275697067, 0.06105685786438454, 0.09805942382099925, 0.244728185770978, 0.07217560217621483, 0.03714467154844523, 0.08204373549242032}, {0.36260277134932123, 0.33338605921414377, 0.37341211813871494, 0.31032189295500573, 0.11075349797140342, 0.49392875922510326, 0.40212195267491707, 0.20213561464328259, 0.21386190010216902, 0.2821926676992191, 0.38212183282127077, 0.4717837429825436, 0.27315941667667964, 0.3282782986057137, 0.1363864260319972, 0.252075892927363, 0.3282782986057137, 0.2973664394673619, 0.34471694437417977, 0.6315508248634661, 0.4573145940671897, 0.21386190010216902, 0.2305979735481111, 0.24239794945485954, 0.4436419715094084, 0.35528557402044253, 0.3368586262897275, 0.24921802750797287, 0.5214061557669919, 0.3401057485922143, 0.4551585459242788, 0.18306483824645223, 0.3414498799813204, 0.2326280119473086, 0.2671021820347493, 0.1755391270171086, 0.35323332647243555, 0.15156349382817708, 0.2596522056312417, 0.3414498799813204, 0.6315508248634661, 0.2715993463008873, 0.3889863940272242, 0.39387858342380105, 0.27315941667667964, 0.192778034561501, 0.3848918907598987, 0.2565569199148879, 0.2565569199148879, 0.164064863449395, 0.2671021820347493, 0.39387858342380105, 0.11540601895634113, 0.3211425760803276, 0.3211425760803276, 0.33706961416343634, 0.15156349382817708, 0.8389804548160174, 0.21004766326037763, 0.4436419715094084, 0.2606231165065697, 0.6137154975927329, 0.32233884069829505, 0.14422508945625992, 0.14373832398241443, 0.23685485785507102, 0.18026123081899784, 0.252075892927363, 0.35528557402044253, 0.14373832398241443, 0.19059414486913345, 0.19730828467489414, 0.14203011261892434, 0.11540601895634113, 0.5145353604139822, 0.23285226020156488, 0.5588991348100799, 0.13612889456709001, 0.644168330205597, 0.2118807637791076, 0.2698541527657029, 0.36384119460433445, 0.37941594483102414, 0.17665515929527442, 0.217315882876174, 0.21386190010216902, 0.47119483539583457, 0.3211425760803276, 0.6586571084638284, 0.15785121400166088, 0.5153881610369417}, {0.3283049427073303, 0.6531116491302003, 0.5905753785511494, 0.5837299541653685, 0.2412656461732098, 0.4763170331831033, 0.4828855013303738, 0.47341404292171035, 0.6298996299793058, 0.6396887601732881, 0.56667492932693, 0.363598749459044, 0.623594068112556, 0.6760748610640888, 0.31692805768598975, 0.8542615962962173, 0.6760748610640888, 0.3622234939464709, 0.6983502569416152, 0.4201394684767121, 0.3883458834702776, 0.6587679804298824, 0.5039225600832596, 0.7328303877599036, 0.5049466882088086, 0.6086119568362842, 0.5242649894564053, 0.5347284443498899, 0.3169279965291077, 0.5741775418980436, 0.29507584305211765, 0.502696075811291, 0.4664947967847735, 0.6109576645661827, 0.5003629283347639, 0.4703666408249716, 0.43201920497271046, 0.38117821206530056, 0.4532262537098153, 0.4664947967847735, 0.42606536748968665, 0.7090570090174625, 0.40318151609457736, 0.3672091756219776, 0.623594068112556, 0.5585496288686506, 0.5589315375688537, 0.9308076059276079, 0.9308076059276079, 0.42682493797148613, 0.5003629283347639, 0.3672091756219776, 0.2569552665019566, 0.48015448401424593, 0.4589331643711987, 0.627451528107299, 0.38117821206530056, 0.3777732682912109, 0.5913235375598095, 0.5049466882088086, 0.9779895192930954, 0.35096982499388396, 0.6924571829801266, 0.34776351644609477, 0.3486539283706802, 0.6807182614738685, 0.502696075811291, 0.8542615962962173, 0.6086119568362842, 0.3486539283706802, 0.5475835758854761, 0.4115617703127843, 0.34142074348430035, 0.2569552665019566, 0.42606536748968665, 0.582056735663994, 0.3215598337112625, 0.31420028390353505, 0.3326933282463009, 0.6172541449143835, 0.7601791304388044, 0.44951323008645033, 0.4845210356060938, 0.4703702903528434, 0.6877913149496548, 0.6587679804298824, 0.48095459739260077, 0.4589331643711987, 0.3845885236923193, 0.3962568627871364, 0.4373112308181276}, {0.5878999790219118, 0.6660830225360843, 0.7214067979510154, 0.6146703642062691, 0.25250522893818106, 0.7850214216850608, 0.6804126994729162, 0.43915577698270536, 0.44853843745159944, 0.5400252144878925, 0.7126937363326054, 0.697720148169583, 0.5687406387825596, 0.6406765302020511, 0.3097574295607795, 0.5229223754885685, 0.6543116337208763, 0.5401144578440077, 0.6087537340769238, 0.8958331319251255, 0.6446598916614747, 0.455703291270584, 0.49085432762282527, 0.5021962011093193, 0.8361534274644449, 0.6802185384757536, 0.6696578863411758, 0.4951159315578734, 0.621514584943975, 0.6803207650199721, 0.5641670166624357, 0.39785459484421815, 0.5970722396721991, 0.4967641926268026, 0.5524228334322167, 0.3877780397759273, 0.663842851333029, 0.33874898985102564, 0.5346502329510563, 0.6064697729226359, 0.8680682490003333, 0.5659778590129481, 0.6859575685870056, 0.6537226022843575, 0.5687406387825596, 0.4192811222837311, 0.7494406030309323, 0.5326617276365301, 0.6251762185754094, 0.3621575516619275, 0.5524228334322167, 0.6537226022843575, 0.26295437935644733, 0.6459230113635699, 0.5524228334322167, 0.6745922712949144, 0.33874898985102564, 0.7613195272704645, 0.4443000615595742, 0.8361534274644449, 0.5416367976717765, 0.7098305623075273, 0.26295437935644733, 0.6251762185754094, 0.32562661047502606, 0.3212445749905807, 0.49058280938766496, 0.39785459484421815, 0.5229223754885685, 0.6802185384757536, 0.3212445749905807, 0.4144321983855404, 0.42738154285339214, 0.317449891042558, 0.26295437935644733, 0.8680682490003333, 0.4969859373642967, 0.6340058241331309, 0.30925790432305067, 0.6543135747424424, 0.44916663846224325, 0.5621908239146398, 0.6875383743405085, 0.727347097710516, 0.3902664733515572, 0.463176261975119, 0.455703291270584, 0.7850214216850608, 0.6297532741317582, 0.7344540508102547, 0.34845743530070844, 0.7565760876403296}, {0.5853466127349797, 0.6382378950258151, 0.627066316174943, 0.5073189015880656, 0.23031612456919048, 0.5652665173539527, 0.5071382924879546, 0.48961051183129006, 0.4216919548249917, 0.4594590638486979, 0.5759379621801684, 0.6378707456737754, 0.6430794839775401, 0.5828065852727801, 0.3107901647578541, 0.49097457856858145, 0.609188633396263, 0.6427902584625819, 0.5360239865991351, 0.5944128683370846, 0.45940284504181256, 0.4330488297234176, 0.5736075732605529, 0.45973446528271655, 0.6871463428454622, 0.5729395228659844, 0.8262208952898464, 0.404161039214674, 0.49370896586788005, 0.7418794720042783, 0.43861636407894167, 0.37538411759379536, 0.4560453517961799, 0.5436563080003016, 0.6917986775126241, 0.3894898012856203, 0.9025359461746112, 0.35731421268282604, 0.6819154495019832, 0.45002749174260565, 0.5783327270140174, 0.6235680099584062, 0.7899207820297116, 0.6841034478131901, 0.6430794839775401, 0.4107645987789902, 0.6924271643632807, 0.5078150236208057, 0.558515572750414, 0.34382594367917935, 0.6917986775126241, 0.6841034478131901, 0.24254015272427812, 0.8966490986936607, 0.6917986775126241, 0.6697292670743481, 0.3261485924122979, 0.52957127003555, 0.40720882220706023, 0.6871463428454622, 0.5255525898045323, 0.5610117336367288, 0.24254015272427812, 0.558515572750414, 0.3246445954746075, 0.30073449644164785, 0.44423315954107934, 0.37538411759379536, 0.49097457856858145, 0.5729395228659844, 0.30073449644164785, 0.40189997358069657, 0.48973064741970534, 0.29573058528323787, 0.24254015272427812, 0.5783327270140174, 0.5539145484712927, 0.4932969662595558, 0.31147987476334815, 0.48284105682561607, 0.41631803546411017, 0.6035709218418053, 0.9025359461746112, 0.8706436150271042, 0.3955606896735247, 0.44534100432858525, 0.4330488297234176, 0.5652665173539527, 0.8888165900417602, 0.5079848115083141, 0.3246814520361221, 0.5281140075652205}, {0.4040849278476106, 0.6871374789169522, 0.607930588316035, 0.5124293922161332, 0.22899337682276524, 0.49029317967264036, 0.4604205660067897, 0.5693042905223, 0.4848443379460517, 0.48256131512596095, 0.54907518664247, 0.43920032743752524, 0.9666781638143975, 0.6317611241702327, 0.3259712388416142, 0.5781427948019839, 0.6600908800441028, 0.4571092653088486, 0.5772948030034958, 0.46289541636860865, 0.3883847751535786, 0.5050909012985781, 0.6792370373609954, 0.5235565615431769, 0.5709450762154149, 0.571244408107899, 0.7127018848389494, 0.4144833926294817, 0.3614775210642735, 0.7337973744796382, 0.32904054108836056, 0.4201028645803482, 0.4134431133609015, 0.717927819920235, 0.7349256750983015, 0.44577792343559935, 0.6327822595055979, 0.3972052008817485, 0.6436651266512621, 0.4042544437733279, 0.46066825218427865, 0.9052986990168402, 0.5140113579601631, 0.45874398348483975, 0.9666781638143975, 0.47936578596894786, 0.6340043720513583, 0.6096917022647185, 0.6041097020430324, 0.37674273703308697, 0.7349256750983015, 0.45874398348483975, 0.24396556663488023, 0.6628994778057045, 0.7349256750983015, 0.710670133783668, 0.35292137701064946, 0.410786649804247, 0.4553423930166211, 0.5709450762154149, 0.6450778265048361, 0.40520565294793986, 0.24396556663488023, 0.6041097020430324, 0.3475770719262851, 0.31741848581719806, 0.49821317683499816, 0.4201028645803482, 0.5781427948019839, 0.571244408107899, 0.31741848581719806, 0.4641546979981425, 0.5198642060548274, 0.3105396592604629, 0.24396556663488023, 0.46066825218427865, 0.7236972167756712, 0.3639115113803925, 0.32581988769033976, 0.3664957492757567, 0.47199372092398956, 0.8394025490639572, 0.5711986612091546, 0.6239502122842822, 0.4543324228831938, 0.5275884858166443, 0.5050909012985781, 0.49029317967264036, 0.6327822595055979, 0.4065932047339179, 0.34842288059181514, 0.4521578344488226}, {0.3303771727391284, 0.5558342184363364, 0.4747709159173718, 0.512676916106224, 0.42979919721825577, 0.4349059790521831, 0.4498679872734432, 0.48790382224152495, 0.754656273152157, 0.5650273215933177, 0.47417027884568186, 0.352175232681653, 0.4984977909091041, 0.5143295996160119, 0.4801678427724467, 0.6176684718652345, 0.5043513019921179, 0.3539615171559334, 0.5337584289089268, 0.8742208824183316, 0.3959399573306877, 0.39553035918020707, 0.7305248647140226, 0.47463713025124726, 0.6459909537207891, 0.44229531668341204, 0.48320472845512674, 0.44357650520182834, 0.5745464663650592, 0.32629734477640066, 0.4634036461639089, 0.31704779183429366, 0.8559853149008082, 0.4635328648089157, 0.5360748401707424, 0.44821060851806604, 0.6121355301276576, 0.4148925570551511, 0.7735920594321766, 0.42504053219282234, 0.4332601927735502, 0.4010703583125289, 0.5122294085225207, 0.383359088664288, 0.360100695198136, 0.4984977909091041, 0.7402543652150156, 0.45766812326896844, 0.5868084061568241, 0.5868084061568241, 0.35479101800893387, 0.5242322584778674, 0.8060460792749096, 0.44821060851806604, 0.6589788541719785, 0.360100695198136, 0.4671859776992495, 0.42462678226964784, 0.44821060851806604, 0.48376244444925165, 0.6726760180760493, 0.37389519958042655, 0.7579263454468202, 0.44229531668341204, 0.5712486357338957, 0.3518284247131847, 0.4984977909091041, 0.4671859776992495, 0.5242322584778674, 0.5374538004912119, 0.6622334821981898, 0.6589788541719785, 0.9073120174923072, 0.6176684718652345, 0.4911568357899093, 0.6707928853106914, 0.5464216801048268, 0.7753192349820589, 0.4281628461565618, 0.6507232723126483, 0.4671859776992495, 0.4010703583125289, 0.5360748401707424, 0.3340995595004867, 0.45811526406562514, 0.34436906263704675, 0.7579263454468202, 0.5262659093039828, 0.3983511264183791, 0.40551493972575997, 0.6294167399610191, 0.704580851371602, 0.754656273152157, 0.4349059790521831, 0.4148925570551511, 0.3818675529825827, 0.7850392757395658, 0.41081269549970517}, {0.3241883608923984, 0.5755033179006485, 0.5213305084154424, 0.6094768543574454, 0.32965922780329454, 0.4821296401545149, 0.5141183584546203, 0.4353631752558958, 0.7357476073122209, 0.7161064815204241, 0.5341787818640245, 0.35379897672390975, 0.49315146384252373, 0.5733682738328353, 0.3627807353954677, 0.691186987708797, 0.5526433022911856, 0.3399771388984234, 0.6184943246584444, 0.6292324850327492, 0.41335890879794646, 0.43658461699777407, 0.7087538580574867, 0.43726898365719047, 0.7684836846829235, 0.4781094902152485, 0.5385886010955131, 0.4502077656033666, 0.7487225829629418, 0.33098846967985757, 0.48039175430957987, 0.3236644451992359, 0.6335168043483723, 0.5462260354287176, 0.5002885297326766, 0.4302624182140076, 0.4520825919806606, 0.41018732995835977, 0.5397466879550535, 0.40320175970828626, 0.49604107644477946, 0.43164438371676683, 0.511598081120847, 0.3971371024323433, 0.3590014940252003, 0.49315146384252373, 0.5817633724803424, 0.48918949676722645, 0.6282607958969492, 0.6007530607650182, 0.37567797119895974, 0.5953592498294522, 0.5449632442351711, 0.4302624182140076, 0.8177240054902661, 0.3683107198553461, 0.3493504719199841, 0.4233651447765453, 0.4302624182140076, 0.514291349408685, 0.6283659730507348, 0.47137622486089065, 0.39720104268993156, 0.820765789004322, 0.4781094902152485, 0.6007530607650182, 0.3614935151603095, 0.49315146384252373, 0.3493504719199841, 0.5953592498294522, 0.39521879492014245, 0.4689707984604567, 0.8177240054902661, 0.6407834313898242, 0.691186987708797, 0.5341787818640245, 0.4689707984604567, 0.39974304281848827, 0.6112018059688934, 0.376401930216886, 0.46177906011650555, 0.3493504719199841, 0.43164438371676683, 0.5002885297326766, 0.34254950698072995, 0.34992145507484806, 0.36008090062219544, 0.820765789004322, 0.5311464447001859, 0.3971371024323433, 0.41641193509920005, 0.4914398810755989, 0.6814174380216631, 0.7357476073122209, 0.4821296401545149, 0.41018732995835977, 0.41115841973972567, 0.5383117588374553, 0.44977191002982897}};
        // Tall
//        double[][] test2 = {{10,19, 8,15},
//                {10,18, 7,17},
//                {13,16, 9,14},
//                {12,19, 8,18},
//                {14,17,10,19}};
//        // Wide
//        double[][] test3 = {{10,19,8,15,14},
//                {10,18,7,17,17},
//                {13,16,9,14,10},
//                {12,19,8,18,19}};

//        System.out.println(hgAlgorithm(test1, "min"));
        System.out.println(hgAlgorithm(test1, "max"));
//        System.out.println(hgAlgorithm(test2, "min"));
//        System.out.println(hgAlgorithm(test2, "max"));
//        System.out.println(hgAlgorithm(test3, "min"));
//        System.out.println(hgAlgorithm(test3, "max"));
    }

}