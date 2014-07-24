package examplepackage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import agents.bayesianopponentmodel.OpponentModel;
import agents.bayesianopponentmodel.OpponentModelUtilSpace;
import negotiator.Agent;
import negotiator.AgentID;
import negotiator.Bid;
import negotiator.actions.Accept;
import negotiator.actions.Action;
import negotiator.actions.Offer;
import negotiator.analysis.BidSpace;
import negotiator.issue.Issue;
import negotiator.issue.IssueDiscrete;
import negotiator.issue.IssueInteger;
import negotiator.issue.IssueReal;
import negotiator.issue.Value;
import negotiator.issue.ValueInteger;
import negotiator.issue.ValueReal;

/**
 * @author W.Pasman
 * Some improvements over the standard SimpleAgent.
 * 
 * Random Walker, Zero Intelligence Agent
 */
public class AffectiveAgent extends Agent
{
	private enum Emotions {HAPPY, SAD};
	public enum AgentLabel {A, B};
	public enum NegotiationType {Distributive, Integrative};
	public enum EvaluationType {BATNA, FAIR, MAX};
	
	private Action opponentLastAction = null;
	private Action selfLastAction  = null;
	
	private List<Double> opponentEntropyHistory;
	
	private List<ArrayList<String>> opponentBidHistory;
	private ArrayList<Integer> acceptedOffersCount = new ArrayList<Integer>();
	private ArrayList<Integer> totalOffersCount    = new ArrayList<Integer>();
	
	private Bid opponentLastBid = null;
	private Bid selfLastBid     = null;
	
	private ArrayList<Double> issueWeightsA, issueWeightsB;
	private HashMap<AgentLabel, ArrayList<Double>> agentsIssueWeight = new HashMap<AgentLabel, ArrayList<Double>>();
	
	private AgentLabel agentLabel;
	
	private OpponentModel fOpponentModel;
	
	private ArrayList<Bid> myPreviousBids;
	
	private Action actionOfPartner=null;
	
	private int nRecords;
	private int nLamps;
	private int nPaintings;
	
	public final int max_nRecords = 3;
	public final int max_nLamps = 2;
	public final int max_nPaintings = 1;
	
	private final NegotiationType negotiationType = NegotiationType.Distributive;
	
	/** Note: {@link SimpleAgent} does not account for the discount factor in its computations */ 
	private static double MINIMUM_BID_UTILITY = 0.0;

	/**
	 * init is called when a next session starts with the same opponent.
	 */
	public void init()
	{
		opponentBidHistory = new ArrayList<ArrayList<String>>();
		
		MINIMUM_BID_UTILITY = utilitySpace.getReservationValueUndiscounted();
		
		String agentID = readAgentID();
		if(!agentID.equals(null)) setAgentID(new AgentID(agentID));
		
		System.out.println("Reservation Value Undiscounted: " + MINIMUM_BID_UTILITY);
		
		System.out.println("Reservation Value: " + utilitySpace.getReservationValue());
		
		myPreviousBids = new ArrayList<Bid>();
		
		selfLastAction = null;
		actionOfPartner = null;

		prepareOpponentModel();
		
//		getIssueWeights();
		
//		System.out.println(">>> Label " + AgentLabel.A + ": " + agentsIssueWeight.get(AgentLabel.A));
//		System.out.println(">>> Label " + AgentLabel.B + ": " + agentsIssueWeight.get(AgentLabel.B));
		
	}

	private String readAgentID() {
		try {
			File file = new File("agentid.txt");
			if (!file.exists()) {
				file.createNewFile();
				return "A";
			}
			else {
				if(file.delete()) return "B";
				else return null;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private void prepareOpponentModel() {
		fOpponentModel = new OpponentModel();
	}
	
	private double evaluateUtilityOfBidOnPareto(EvaluationType evalType, NegotiationType negoType) throws Exception {
		BidSpace bs = new BidSpace(utilitySpace, new OpponentModelUtilSpace(fOpponentModel), false, true);
		double obtainedUtility = bs.ourUtilityOnPareto(fOpponentModel.getNormalizedUtility(opponentLastBid));

		switch (evalType)
		{
			case FAIR:
				return (obtainedUtility - getFairUtilityOnPareto(bs));
			case MAX:
				return (getMaxUtility() - obtainedUtility);
			default:
				return (obtainedUtility - utilitySpace.getReservationValue());
		}
	}
	
	private double getFairUtilityOnPareto(BidSpace bidSpace) throws Exception {
		
		HashMap<Integer, Value> values = new HashMap<Integer, Value>();
		ArrayList<Issue> issues = utilitySpace.getDomain().getIssues();
		
		for(Issue lIssue:issues) 
			values.put(lIssue.getNumber(), ((IssueDiscrete)lIssue).getValue(lIssue.getNumber()-1));
		
		// The question is what does 50% mean when we have partial offers?!
		double fairUtility = bidSpace.ourUtilityOnPareto(fOpponentModel.getNormalizedUtility(new Bid(utilitySpace.getDomain(), values)));
		
		return fairUtility;
	}
	
	private double getMaxUtility() {
		
		if (negotiationType == NegotiationType.Distributive)
		{
			if(this.agentLabel == AgentLabel.A)
				return 30*max_nRecords + 15*max_nLamps + 5*max_nPaintings;
			else
				return 30*max_nRecords + 15*max_nLamps + 5;
		}
		else if (negotiationType == NegotiationType.Integrative)
		{
			if(this.agentLabel == AgentLabel.A)
				return 20*max_nRecords + 10*max_nLamps + 5*max_nPaintings;
			else
				return 10*max_nRecords + 30*max_nLamps + 5;
		}
		
		return -1;
	}
	
	private void appraise() {
		
		System.out.println("Expressed Emotion: " + Emotions.SAD);
		
		System.out.println("Expressed Emotion: " + Emotions.HAPPY);
	}
	
	private Action proposeInitialBid() throws Exception {
		Bid lBid = null;
		lBid = utilitySpace.getMaxUtilityBid();
		selfLastBid = lBid;
		return new Offer(getAgentID(), lBid);
	}
	
	@Override
	public String getVersion() { return "3.1"; }
	
	@Override
	public String getName()
	{
		return "Affective Agent";
	}

	public void ReceiveMessage(Action opponentAction) 
	{
		actionOfPartner = opponentAction;
		
		if (opponentAction instanceof Offer)
		{
			try { 
				
				System.out.println("Affective Agent >>> Opponent's Bid: " + ((Offer)opponentAction).getBid());
				System.out.println("Affective Agent >>> Utility of Opponent's Bid: " + utilitySpace.getUtility(((Offer)opponentAction).getBid()));
				
				opponentLastBid = ((Offer)opponentAction).getBid();
				addOpponentLastBidToHistory(opponentLastBid);
				
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private void addOpponentLastBidToHistory(Bid opponentLastBid) throws Exception {
		
		ArrayList<String> tempBidList = new ArrayList<String>();
		
		for (int i = 1 ; i <= opponentLastBid.getIssues().size() ; i++)
		{
			tempBidList.add(opponentLastBid.getValue(i).toString());
		}
		
		opponentBidHistory.add(tempBidList);
	}
	
	public Double calculateShannonEntropy(/*List<String> values*/) {
		
		int counter = 0;
		
		HashMap<String, Integer> map = new HashMap<String, Integer>();
		
		for (int i = 0 ; i < opponentBidHistory.size() ; i++)
		{
			for (String sequence : /*values*/ opponentBidHistory.get(i)) {
				if (!map.containsKey(sequence)) {
					map.put(sequence, 0);
			    }
			    map.put(sequence, map.get(sequence) + 1);
			    counter++;
			}
		}
		
		Double result = 0.0;
		if (counter != 0) {
			for (String sequence : map.keySet()) {
				Double frequency = (double) map.get(sequence) / counter; // values.size()
				result -= frequency * (Math.log(frequency) / Math.log(2));
			}
		}
		
		opponentEntropyHistory.add(result);
		
		return result;
	}

	private Bid getNextBid(Bid pOppntBid) throws Exception {
		Bid lBid = null;
		lBid = utilitySpace.getMaxUtilityBid(); // This can be min or max under different conditions!
		selfLastBid = lBid;
		return lBid;
	}
	
	public Action chooseAction()
	{
		Action action = null;
		Bid opponentBid = null;

		try {
			opponentBid = ((Offer) actionOfPartner).getBid();

			// Check if action type is Offer and not accept! --> Do this before calling this function. 
			
			if (selfLastAction == null)
				action = proposeInitialBid();
			else {
				double offeredUtility = utilitySpace.getUtility(opponentBid);
				
				double time = timeline.getTime();
				
				if (isAcceptable(offeredUtility, time)) {
					action = new Accept(getAgentID()); // opponent's bid higher than util of my last bid! accepted
				} 
				else {
					Bid lnextBid = getNextBid(opponentBid); // Be careful about null bids. 

//					if (lnextBid == null)
//						lnextBid = myPreviousBids.get(myPreviousBids.size() - 1);
					
					action = new Offer(getAgentID(), lnextBid);
				}
			}
			
			opponentLastAction = actionOfPartner;
			
		} catch (Exception e) {
			System.out.println("Exception in chooseAction:" + e.getMessage());
			e.printStackTrace();
			action = new Offer(getAgentID(), selfLastBid); // Check whether self last bid gets updated properly.
		}
		
		selfLastAction = action;
		
		if (selfLastAction instanceof Offer) {
			myPreviousBids.add(((Offer) selfLastAction).getBid());
			selfLastBid = ((Offer) selfLastAction).getBid();
		}
		
		return action;
	}

	private boolean isAcceptable(double offeredUtilFromOpponent, double time) throws Exception
	{
		double P = Paccept(offeredUtilFromOpponent,time);
		if (P > Math.random()) // This shouldn't be random!
			return true;		
		return false;
	}
	
	/**
	 * This function determines the accept probability for an offer.
	 * At t=0 it will prefer high-utility offers.
	 * As t gets closer to 1, it will accept lower utility offers with increasing probability.
	 * it will never accept offers with utility 0.
	 * @param u is the utility 
	 * @param t is the time as fraction of the total available time 
	 * (t=0 at start, and t=1 at end time)
	 * @return the probability of an accept at time t
	 * @throws Exception if you use wrong values for u or t.
	 * 
	 */
	double Paccept(double u, double t1) throws Exception
	{
		double t=t1*t1*t1; // steeper increase when deadline approaches.
		if (u<0 || u>1.05) throw new Exception("utility "+u+" outside [0,1]");
		// normalization may be slightly off, therefore we have a broad boundary up to 1.05
		if (t<0 || t>1) throw new Exception("time "+t+" outside [0,1]");
		if (u>1.) u=1;
		if (t==0.5) return u;
		return (u - 2.*u*t + 2.*(-1. + t + Math.sqrt(sq(-1. + t) + u*(-1. + 2*t))))/(-1. + 2*t);
	}

	double sq(double x) { return x*x; }
	
	// These two methods should be implemented for both of the agents!
	// Also, it should be checked whether it is zero indexed!
	public int getAcceptedOffersCount(int turnIndex) {
		return acceptedOffersCount.get(turnIndex);
	}
	
	public int getTotalOffersCount(int turnIndex) {
		return totalOffersCount.get(turnIndex);
	}
}
