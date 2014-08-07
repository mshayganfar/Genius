package examplepackage;

import java.util.HashMap;
import java.util.List;
import negotiator.Bid;
import negotiator.analysis.BidSpace;
import negotiator.utility.UtilitySpace;
import agents.anac.y2013.MetaAgent.portfolio.thenegotiatorreloaded.BidIterator;
import agents.bayesianopponentmodel.BayesianOpponentModel;
import agents.bayesianopponentmodel.OpponentModelUtilSpace;
import examplepackage.AffectiveAgent;

/**
 * @author M. Shayganfar
 * 
 * Appraisal Variables: Desirability, Controllability and Unexpectedness.
 * Emotions: Worriedness, Sadness, Anger, Joy, Neg. Surprise.
 */

public class Appraisal extends AffectiveAgent{
	
	private long startingTime = 0;
	
	private double rSquared = 0.0;
	
	private Double minUtility=null, maxUtility=null;
	
	private double beta1, beta0;
	
	private final String[] userModels = {"RPL", "RLP", "PLR", "PRL", "LPR", "LRP"};
	
	private HashMap<String, Double> probabilities = new HashMap<String, Double>();
	private HashMap<String, Double> lastProbabilities = new HashMap<String, Double>();
	
	private FairnessType fairnessType = FairnessType.NASH;
	
	private enum FairnessType {NASH, KALAI};
	private enum Intentionality {INTENTIONAL, UNINTENTIONAL};
	private enum Time {SECONDS, MINUTES, TURNS};
	private enum Emotions {WORRIEDNESS, HAPPY, SAD, ANGER, SURPRISE, HOPE};
	
	private Intentionality intenStatus = Intentionality.INTENTIONAL;
	
	public boolean isDesirable(UtilitySpace utilSpace, BayesianOpponentModel opponentModel, Bid opponentLastBid, EvaluationType evalType, double threshold) throws Exception {
		
		//BidSpace bs = new BidSpace(utilitySpace, new OpponentModelUtilSpace(opponentModel), false, true);
		//double obtainedUtility = bs.ourUtilityOnPareto(opponentModel.getNormalizedUtility(opponentLastBid));
		System.out.println("BATNA: " + utilSpace.getReservationValueUndiscounted());
		switch (evalType)
		{
			case BATNA:
				if ((utilSpace.getUtility(opponentLastBid) - utilSpace.getReservationValueUndiscounted()) >= threshold) return true; else return false;
			case MAX:
				if ((utilSpace.getUtility(utilSpace.getMaxUtilityBid()) - utilSpace.getUtility(opponentLastBid)) <= threshold) return true; else return false;
			case FAIR:
				if ((utilSpace.getUtility(opponentLastBid) - getFairUtilityOnPareto(utilSpace, new BidSpace(utilSpace, new OpponentModelUtilSpace(opponentModel), false, true), getFairnessType())) >= threshold) return true; else return false;
			default:
				System.out.println("Appraisal--Desirability Failed!");
				return false;
		}
	}
	
	private boolean isControllable(UtilitySpace utilSpace, List<Bid> bidHistory, Bid opponentLastBid, double dValue, double thresholdValue) throws Exception {
		
		int i = 0;
		int turnCount = bidHistory.size(); // Or implement getTurnCount()!
		
		double accDenom = 0.0;
		double weight = 0.0;
		double numerator = 0.0;
		double controllabilityResult = 0.0;
		
		if(turnCount >1)
		{
			updateAcceptedOffersCount();
			updateTotalOffersCount();
			
			for (i = 1 ; i <= turnCount ; i++)
				accDenom += Math.pow(0.5, i);
			
			for (i = turnCount ; i >= 1 ; i--)
			{
				weight = (double)Math.pow(0.5, i)/accDenom;
				numerator += weight*((getTotalOffersCount(i) != 0) ? (getAcceptedOffersCount(i)/getTotalOffersCount(i)) : 0.5); 
			}
			
			controllabilityResult = ((double)numerator/(turnCount*Math.pow(turnCount, dValue))) + getAlphaValue(utilSpace, opponentLastBid);
			
			System.out.println("++++++ Controllability Value: " + controllabilityResult);
			
			if (controllabilityResult <= thresholdValue) return false; else return true;
		}
		
		return true; // In the very first step there is hope no matter what! 
	}
	
	private boolean isControllable(Bid opponentLastBid, double thresholdValue) throws Exception {
		
		if((getUtility(utilitySpace.getMaxUtilityBid()) - thresholdValue) < getUtility(opponentLastBid)) return true; else return false;
	}
	
	private int isControllable(UtilitySpace utilSpace, List<Bid> bidHistory, double regressionValidityThreshold, double distanceToAspirationThreshold, long time) throws Exception {
		
		computeLinearRegression(utilSpace, bidHistory);
		
		if(getrSquaredValue() >= regressionValidityThreshold)
			if (estimateUtilityDistanceToAspirationValueAtTime(utilSpace, time) > distanceToAspirationThreshold) return 0; else return 1; 
		else
			System.out.println("Invalid regression line!");
		
		return -1;
	}
	
	public boolean isControllable(UtilitySpace utilSpace, List<Bid> bidHistory, Bid opponentLastBid, double regressionValidityThreshold, double distanceToAspirationRegressionThreshold, long desiredNegotiationEndingTime, double dTimeGrowthValue, double nonRegressionContrallabilityThresholdValue) throws Exception {
		
		double contrllability = isControllable(utilSpace, bidHistory, regressionValidityThreshold, distanceToAspirationRegressionThreshold, desiredNegotiationEndingTime);
		
		if(contrllability != -1)
		{
			if (contrllability == 0) return false;
			else if (contrllability == 1) return true;
		}
		else if (isControllable(utilSpace, bidHistory, opponentLastBid, dTimeGrowthValue, nonRegressionContrallabilityThresholdValue)) return true;
		
		return false;
	}
	
	// This method is not debugged yet!
	public boolean isUnexpected(Bid opponentLastBid, double thresholdValue) throws Exception { 
		
		updateUserModelProbabilities(opponentLastBid);

		if (getUserModelDistance() <= thresholdValue) return false; else return true;
	}
	
	public boolean isUnexpected(UtilitySpace utilSpace, List<Bid> bidHistory, double thresholdValue) throws Exception {
		
		if (Math.log(1 + getMaxHistoryUtility(utilSpace, bidHistory) - utilSpace.getUtility(bidHistory.get(bidHistory.size()-1))) >= thresholdValue) return true; else return false;
	}
	
	public boolean isUnexpected(double thresholdValue, List<Bid> bidHistory, UtilitySpace utilSpace) throws Exception {
		
		if ((1 - utilSpace.getUtility(bidHistory.get(bidHistory.size()-1))) >= thresholdValue) return true; else return false;
	}

	public boolean isTemporalStatusFuture(UtilitySpace utilSpace, List<Bid> bidHistory, BayesianOpponentModel opponentModel, long time, double rSquaredThresholdValue, double acceptableDistanceToAspirationValue) throws Exception {

		computeLinearRegression(utilSpace, bidHistory);

		if (getrSquaredValue() >= rSquaredThresholdValue)
			if(estimateUtilityDistanceToAspirationValueAtTime(utilSpace, time) <= acceptableDistanceToAspirationValue)
				return true;

		return false;
	}
	
	private void initializeUserModelProbabilities() {
		
		for (int i = 0 ; i < userModels.length ; i++)
		{
			probabilities.put(userModels[i], (double)100/userModels.length);
		}
		
		lastProbabilities = (HashMap)probabilities.clone();
	}
	
	private void initializeUserModelProbabilities(String defaultUserModel, Double probability) {
		
		probabilities.put(defaultUserModel, probability);
		
		for (int i = 0 ; i < userModels.length ; i++)
		{
			if(!userModels[i].equals(defaultUserModel)) 
				probabilities.put(userModels[i], (double)(100-probability)/(userModels.length-1));
		}
		
		lastProbabilities = (HashMap)probabilities.clone();
	}
	
	public boolean isIntentional() {
		if (intenStatus == Intentionality.INTENTIONAL) return true; else return false;
	}
	
	public boolean isOpponentCause() {
		return true;
	}
	
	private double getFairUtilityOnPareto(UtilitySpace utilSpace, BidSpace bidSpace, FairnessType fairType) throws Exception {
		
		switch(fairType)
		{
			case NASH:
				return utilSpace.getUtility(bidSpace.getNash().getBid());
			case KALAI:
				return utilSpace.getUtility(bidSpace.getKalaiSmorodinsky().getBid());
		}
		
		return -1.0;
	}
	
	private long getTime(Time time) {
		
		switch(time)
		{
			case SECONDS:
				return (System.currentTimeMillis()-startingTime)/1000;
			case MINUTES:
				return (System.currentTimeMillis()-startingTime)/60000;
			case TURNS:
				return getTurnCount();
			default:
				System.out.println("Wrong type of requested time!");
				return -1;
		}
	}
	
	public void startClock() {
		startingTime = System.currentTimeMillis();
	}
	
	private FairnessType getFairnessType() {
		return fairnessType;
	}
	
	public void setFairnessType(FairnessType fairnessType) {
		this.fairnessType = fairnessType;
	}
	
	private void updateUserModelProbabilities(Bid opponentLastBid) throws Exception {
		
		updateUserModelProbabilityRecord(opponentLastBid);
		updateUserModelProbabilityLamp(opponentLastBid);
		updateUserModelProbabilityPainting(opponentLastBid);
	}
	
	private void updateUserModelProbabilityRecord(Bid opponentLastBid) throws Exception {
		
		int issueAmount = Integer.parseInt(opponentLastBid.getValue(1).toString());
		
		switch (issueAmount) {
			case 0:
				for (int i= 0 ; i < userModels.length ; i++)
				{
					if(userModels[i].equals("RPL") || userModels[i].equals("RLP")) {
						probabilities.put("RPL", probabilities.get("RPL")*3);
						probabilities.put("RLP", probabilities.get("RLP")*3);
					}
					else if(userModels[i].equals("PRL") || userModels[i].equals("LRP")) {
						probabilities.put("PRL", probabilities.get("PRL")*2);
						probabilities.put("LRP", probabilities.get("LRP")*2);						
					}
					else if(userModels[i].equals("PLR") || userModels[i].equals("LPR")) {
						probabilities.put("PLR", probabilities.get("PLR")*1);
						probabilities.put("LPR", probabilities.get("LPR")*1);
					}
				}
				break;
			case 1:
				for (int i= 0 ; i < userModels.length ; i++)
				{
					if(userModels[i].equals("RPL") || userModels[i].equals("RLP")) {
						probabilities.put("RPL", probabilities.get("RPL")*2);
						probabilities.put("RLP", probabilities.get("RLP")*2);
					}
					else if(userModels[i].equals("PRL") || userModels[i].equals("LRP")) {
						probabilities.put("PRL", probabilities.get("PRL")*1.5);
						probabilities.put("LRP", probabilities.get("LRP")*1.5);						
					}
					else if(userModels[i].equals("PLR") || userModels[i].equals("LPR")) {
						probabilities.put("PLR", probabilities.get("PLR")*1);
						probabilities.put("LPR", probabilities.get("LPR")*1);
					}
				}
				break;
			case 2:
				for (int i= 0 ; i < userModels.length ; i++)
				{
					if(userModels[i].equals("RPL") || userModels[i].equals("RLP")) {
						probabilities.put("RPL", (double)probabilities.get("RPL")/2);
						probabilities.put("RLP", (double)probabilities.get("RLP")/2);
					}
					else if(userModels[i].equals("PRL") || userModels[i].equals("LRP")) {
						probabilities.put("PRL", (double)probabilities.get("PRL")/1.5);
						probabilities.put("LRP", (double)probabilities.get("LRP")/1.5);						
					}
					else if(userModels[i].equals("PLR") || userModels[i].equals("LPR")) {
						probabilities.put("PLR", (double)probabilities.get("PLR")/1);
						probabilities.put("LPR", (double)probabilities.get("LPR")/1);
					}
				}
				break;
			case 3:
				for (int i= 0 ; i < userModels.length ; i++)
				{
					if(userModels[i].equals("RPL") || userModels[i].equals("RLP")) {
						probabilities.put("RPL", (double)probabilities.get("RPL")/3);
						probabilities.put("RLP", (double)probabilities.get("RLP")/3);
					}
					else if(userModels[i].equals("PRL") || userModels[i].equals("LRP")) {
						probabilities.put("PRL", (double)probabilities.get("PRL")/2);
						probabilities.put("LRP", (double)probabilities.get("LRP")/2);						
					}
					else if(userModels[i].equals("PLR") || userModels[i].equals("LPR")) {
						probabilities.put("PLR", (double)probabilities.get("PLR")/1);
						probabilities.put("LPR", (double)probabilities.get("LPR")/1);
					}
				}
				break;
		}
		
		normalizeProbabilities();
	}
	
	private void updateUserModelProbabilityLamp(Bid opponentLastBid) throws Exception {
		
		int issueAmount = Integer.parseInt(opponentLastBid.getValue(2).toString());
		
		switch (issueAmount) {
		case 0:
			for (int i= 0 ; i < userModels.length ; i++)
			{
				if(userModels[i].equals("LPR") || userModels[i].equals("LRP")) {
					probabilities.put("LPR", probabilities.get("LPR")*3);
					probabilities.put("LRP", probabilities.get("LRP")*3);
				}
				else if(userModels[i].equals("PLR") || userModels[i].equals("RLP")) {
					probabilities.put("PLR", probabilities.get("PLR")*2);
					probabilities.put("RLP", probabilities.get("RLP")*2);						
				}
				else if(userModels[i].equals("PRL") || userModels[i].equals("RPL")) {
					probabilities.put("PRL", probabilities.get("PRL")*1);
					probabilities.put("RPL", probabilities.get("RPL")*1);
				}
			}
			break;
		case 1:
			for (int i= 0 ; i < userModels.length ; i++)
			{
				if(userModels[i].equals("LPR") || userModels[i].equals("LRP")) {
					probabilities.put("LPR", (double)probabilities.get("LPR")/2);
					probabilities.put("LRP", (double)probabilities.get("LRP")/2);
				}
				else if(userModels[i].equals("PLR") || userModels[i].equals("RLP")) {
					probabilities.put("PLR", (double)probabilities.get("PLR")/1.5);
					probabilities.put("RLP", (double)probabilities.get("RLP")/1.5);						
				}
				else if(userModels[i].equals("PRL") || userModels[i].equals("RPL")) {
					probabilities.put("PRL", (double)probabilities.get("PRL")/1);
					probabilities.put("RPL", (double)probabilities.get("RPL")/1);
				}
			}
			break;
		case 2:
			for (int i= 0 ; i < userModels.length ; i++)
			{
				if(userModels[i].equals("LPR") || userModels[i].equals("LRP")) {
					probabilities.put("LPR", (double)probabilities.get("LPR")/3);
					probabilities.put("LRP", (double)probabilities.get("LRP")/3);
				}
				else if(userModels[i].equals("PLR") || userModels[i].equals("RLP")) {
					probabilities.put("PLR", (double)probabilities.get("PLR")/2);
					probabilities.put("RLP", (double)probabilities.get("RLP")/2);						
				}
				else if(userModels[i].equals("PRL") || userModels[i].equals("RPL")) {
					probabilities.put("PRL", (double)probabilities.get("PRL")/1);
					probabilities.put("RPL", (double)probabilities.get("RPL")/1);
				}
			}
			break;
		}
		
		normalizeProbabilities();
	}
	
	private void updateUserModelProbabilityPainting(Bid opponentLastBid) throws Exception {
		
		int issueAmount = Integer.parseInt(opponentLastBid.getValue(3).toString());
		
		switch (issueAmount) {
		case 0:
			for (int i= 0 ; i < userModels.length ; i++)
			{
				if(userModels[i].equals("PRL") || userModels[i].equals("PLR")) {
					probabilities.put("PRL", probabilities.get("PRL")*3);
					probabilities.put("PLR", probabilities.get("PLR")*3);
				}
				else if(userModels[i].equals("RPL") || userModels[i].equals("LPR")) {
					probabilities.put("RPL", probabilities.get("RPL")*2);
					probabilities.put("LPR", probabilities.get("LPR")*2);						
				}
				else if(userModels[i].equals("LRP") || userModels[i].equals("RLP")) {
					probabilities.put("LRP", probabilities.get("LRP")*1);
					probabilities.put("RLP", probabilities.get("RLP")*1);
				}
			}
			break;
		case 1:
			for (int i= 0 ; i < userModels.length ; i++)
			{
				if(userModels[i].equals("PRL") || userModels[i].equals("PLR")) {
					probabilities.put("PRL", (double)probabilities.get("PRL")/3);
					probabilities.put("PLR", (double)probabilities.get("PLR")/3);
				}
				else if(userModels[i].equals("RPL") || userModels[i].equals("LPR")) {
					probabilities.put("RPL", (double)probabilities.get("RPL")/2);
					probabilities.put("LPR", (double)probabilities.get("LPR")/2);						
				}
				else if(userModels[i].equals("LRP") || userModels[i].equals("RLP")) {
					probabilities.put("LRP", (double)probabilities.get("LRP")/1);
					probabilities.put("RLP", (double)probabilities.get("RLP")/1);
				}
			}
			break;
		}
		
		normalizeProbabilities();
	}
	
	private void normalizeProbabilities() {
		
		double sum = 0.0;
		
		for (int i= 0 ; i < userModels.length ; i++)
			sum += probabilities.get(userModels[i]);
		
		for (int i= 0 ; i < userModels.length ; i++)
			probabilities.put(userModels[i], (double)probabilities.get(userModels[i])/sum);
	}
	
	private double getUserModelDistance() {
		
		double sum = 0.0;
		
		for (int i= 0 ; i < userModels.length ; i++)
			sum += Math.pow(probabilities.get(userModels[i])-lastProbabilities.get(userModels[i]), 2);
		
		lastProbabilities = (HashMap)probabilities.clone();
		
		return Math.sqrt(sum);
	}
	
	private void computeLinearRegression(UtilitySpace utilSpace, List<Bid> bidHistory) throws Exception {
        
		int n = 1;

        double opponentBidUtility;
        double sumx = 0.0, sumy = 0.0, sumx2 = 0.0;
        
        while(bidHistory.size() >= n) {
            opponentBidUtility = utilSpace.getUtility(bidHistory.get(n-1));
        	sumx  += opponentBidUtility;
            sumx2 += opponentBidUtility * opponentBidUtility;
            sumy  += n;
            n++;
        }
        
        double xbar = ((n-1) != 0) ? (double)sumx/(n-1) : 0.0;
        double ybar = ((n-1) != 0) ? (double)sumy/(n-1) : 0.0;

        double xxbar = 0.0, yybar = 0.0, xybar = 0.0;
        
        for (int i = 1 ; i < n ; i++) {
        	opponentBidUtility = utilSpace.getUtility(bidHistory.get(i-1));
            xxbar += (opponentBidUtility - xbar) * (opponentBidUtility - xbar);
            yybar += (i - ybar) * (i - ybar);
            xybar += (opponentBidUtility - xbar) * (i - ybar);
        }
        
        beta1 = (xxbar != 0.0) ? (double)xybar/xxbar : 0.0;
        beta0 = ybar - beta1 * xbar;

        System.out.println("y = " + beta1 + " * x + " + beta0);

        double ssr = 0.0;
        
        for (int i = 0 ; i < n-1 ; i++) {
            double fit = beta1 * utilSpace.getUtility(bidHistory.get(i)) + beta0;
            ssr += (fit - ybar) * (fit - ybar);
        }
        
        rSquared = (yybar != 0.0) ? (double)ssr/yybar : 0.0;
        
        System.out.println("++++++ R-Squared Value: " + rSquared);
	}
	
	private double getrSquaredValue() {
		return rSquared;
	}
	
	private double estimateUtilityDistanceToAspirationValueAtTime(UtilitySpace utilSpace, long time) throws Exception {
		return Math.abs(utilSpace.getUtility(utilSpace.getMaxUtilityBid()) - getNormalizedUtility(utilSpace, beta1 + (time * beta0)));
	}
	
	private double getNormalizedUtility(UtilitySpace utilSpace, double utilityValue) throws Exception
	{
		double value = 0.0;
		
		if (minUtility == null || maxUtility == null) findMinMaxUtility(utilSpace);
		
		if ((maxUtility-minUtility) != 0)
			value = (double)(utilityValue - minUtility)/(maxUtility - minUtility);
		
		if (Double.isNaN(value) || (value == 0.0)) return 0.0;

		return (double)(utilityValue - minUtility)/(maxUtility - minUtility);
	}
	
	private void findMinMaxUtility(UtilitySpace utilSpace) throws Exception
	{
		double utility;
		
		BidIterator biditer = new BidIterator(utilSpace.getDomain());
		
		minUtility = 1.0;  maxUtility = 0.0;

		while (biditer.hasNext())
		{
			utility = utilSpace.getUtility(biditer.next());
			
			if (minUtility > utility) minUtility = utility; //Equality?!!!
			if (maxUtility < utility) maxUtility = utility;
		}
	}
	
	private double getMaxHistoryUtility(UtilitySpace utilSpace, List<Bid> bidHistory) throws Exception {
		
		double maxUtility = 0.0;
		
		for (int i = 0 ; i < bidHistory.size() ; i++)
			if (maxUtility < utilSpace.getUtility(bidHistory.get(i)))
				maxUtility = utilSpace.getUtility(bidHistory.get(i));
		
		return maxUtility;
	}
	
	private double getAlphaValue(UtilitySpace utilSpace, Bid opponentBid) throws Exception {
		
		double distance = utilSpace.getUtility(utilSpace.getMaxUtilityBid()) - utilSpace.getUtility(opponentBid);
		
		if (distance != 0.0)
			return ((double)1.0/Math.abs(distance) * 0.1);
		else
			return 1.0;
	}
	
	public void appraise() throws Exception {
		
//		if(appraisal.isDesirable(utilitySpace, fOpponentModel, opponentLastBid, EvaluationType.FAIR, 0.5))
//			System.out.println("+++++ Expressed Emotion: " + Emotions.HAPPY);
//		else
//			System.out.println("+++++ Expressed Emotion: " + Emotions.SAD);
		
//		if (appraisal.isControllable(utilitySpace, opponentBidHistory, opponentLastBid, 0.8, 0.1, 180, 0.1, 0.5))
//			System.out.println("+++++ Expressed Emotion: " + Emotions.HOPE);
//		else
//			System.out.println("+++++ Expressed Emotion: " + Emotions.WORRIEDNESS);
		
		if (!isUnexpected(utilitySpace, opponentBidHistory, 0.5))
			System.out.println("+++++ Expressed Emotion: " + Emotions.SURPRISE);
	}
}