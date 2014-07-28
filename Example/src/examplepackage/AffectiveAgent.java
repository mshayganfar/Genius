package examplepackage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import agents.bayesianopponentmodel.OpponentModel;
import negotiator.Agent;
import negotiator.AgentID;
import negotiator.Bid;
import negotiator.actions.Accept;
import negotiator.actions.Action;
import negotiator.actions.Offer;

/**
 * @author M. Shayganfar
 * Some improvements over the standard SimpleAgent.
 * 
 * Random Walker, Affective Intelligence Agent
 */
public class AffectiveAgent extends Agent
{
	private enum Emotions {WORRIEDNESS, HAPPY, SAD, ANGER, SURPRISE};
	public enum AgentLabel {A, B};
	public enum EvaluationType {BATNA, FAIR, MAX};
	
	private Action opponentLastAction = null;
	private Action selfLastAction  = null;
	
	private List<Bid> opponentBidHistory;
	private List<Bid> selfBidHistory;
	
	private ArrayList<Integer> acceptedOffersCount = new ArrayList<Integer>();
	
	private ArrayList<Integer> totalOffersCountAgentA    = new ArrayList<Integer>();
	private ArrayList<Integer> totalOffersCountAgentB    = new ArrayList<Integer>();
	
	private Bid opponentLastOffer = new Bid();
	private Bid selfLastOffer = new Bid();
	
	private Bid opponentLastBid = null;
	private Bid selfLastBid     = null;
	
	private AgentLabel agentLabel;
	
	private OpponentModel fOpponentModel;
	
	private ArrayList<Bid> myPreviousBids;
	
	private Action actionOfPartner=null;
	
	private int turnCount = 0;
	
	/** Note: {@link SimpleAgent} does not account for the discount factor in its computations */ 
	private static double MINIMUM_BID_UTILITY = 0.0;

	/**
	 * init is called when a next session starts with the same opponent.
	 */
	public void init()
	{
		opponentBidHistory = new ArrayList<Bid>();
		selfBidHistory = new ArrayList<Bid>();
		
		MINIMUM_BID_UTILITY = utilitySpace.getReservationValueUndiscounted();
		
		String agentID = readAgentLabel();
		if(!agentID.equals(null)) setAgentID(new AgentID(agentID));
		
		System.out.println("Reservation Value Undiscounted: " + MINIMUM_BID_UTILITY);
		
		System.out.println("Reservation Value: " + utilitySpace.getReservationValue());
		
		myPreviousBids = new ArrayList<Bid>();
		
		selfLastAction = null;
		actionOfPartner = null;

		prepareOpponentModel();
	}

	private String readAgentLabel() {
		try {
			File file = new File("agentid.txt");
			if (!file.exists()) {
				file.createNewFile();
				setAgentLabel(AgentLabel.A);
				return "A";
			}
			else {
				if(file.delete()) {
					setAgentLabel(AgentLabel.B);
					return "B";
				}
				else return null;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private void setAgentLabel(AgentLabel agentLabel) {
		this.agentLabel = agentLabel;
	}
	
	private AgentLabel getAgentLabel() {
		return agentLabel;
	}
	
	private void prepareOpponentModel() {
		fOpponentModel = new OpponentModel();
	}
	
	private void appraise() {
		
		System.out.println("Expressed Emotion: " + Emotions.SAD);
		
		System.out.println("Expressed Emotion: " + Emotions.HAPPY);
	}
	
	private Action proposeInitialBid() throws Exception {
		Bid lBid = null;
		lBid = utilitySpace.getMaxUtilityBid();
		updateSelfLastOffer(lBid);
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
				
				updateOpponentLastOffer(opponentLastBid);
				
				opponentBidHistory.add(((Offer)opponentAction).getBid());
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private Bid getNextBid(Bid pOppntBid) throws Exception {
		Bid lBid = null;
		lBid = utilitySpace.getMaxUtilityBid(); // This can be min or max depending on the strategy!
		selfLastBid = lBid;
		return lBid;
	}
	
	public Action chooseAction()
	{
		Action action = null;
		Bid opponentBid = null;

		setTurnCount(++turnCount);
		
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
					
					updateSelfLastOffer(lnextBid);
					
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
		
		if (action instanceof Offer) selfBidHistory.add(((Offer)action).getBid());
		
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
	
	public int getAcceptedOffersCount(int turnIndex) {
		return acceptedOffersCount.get(turnIndex);
	}
	
	public int getTotalOffersCount(int turnIndex) {
		
		if(getAgentLabel() == AgentLabel.A) return totalOffersCountAgentA.get(turnIndex);
		else if(getAgentLabel() == AgentLabel.B) return totalOffersCountAgentB.get(turnIndex);
		
		return -1;
	}
	
	private void updateAcceptedOffersCount() throws Exception {
		
		int counter = 0;
		
		for(int i = 1 ; i <= opponentBidHistory.get(opponentBidHistory.size()-1).getIssues().size() ; i++)
		{
			for(int j = 1 ; j <= selfBidHistory.get(selfBidHistory.size()-1).getIssues().size() ; j++)
			{
				if(opponentBidHistory.get(opponentBidHistory.size()-1).getValue(i).equals(selfBidHistory.get(selfBidHistory.size()-1).getValue(j)))
				{
					counter++;
				}
			}
		}
		
		acceptedOffersCount.add(counter);
	}
	
	private void updateTotalOffersCount() {
		totalOffersCountAgentA.add(selfBidHistory.get(selfBidHistory.size()-1).getIssues().size());
	}
	
	private void updateOpponentLastOffer(Bid opponentLastOffer) {
		this.opponentLastOffer = opponentLastOffer;
	}
	
	private void updateSelfLastOffer(Bid selfLastOffer) {
		this.selfLastOffer = selfLastOffer;
	}
	
	public int getTurnCount() {
		return this.turnCount/2;
	}
	
	private void setTurnCount(int turnCount) {
		this.turnCount = turnCount;
	}
}
