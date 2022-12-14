/*
 * author: Steven Adriaensen
 * date: 22/01/2014
 * contact: steven.adriaensen@vub.ac.be
 * affiliation: Vrije Universiteit Brussel
 */

import java.util.Vector;

import AbstractClasses.HyperHeuristic;
import AbstractClasses.ProblemDomain;
import AbstractClasses.ProblemDomain.HeuristicType;

public class FairShareILS extends HyperHeuristic {
	//the problem to be solved
	ProblemDomain problem;
	
	//parameters
	double T = 0.5;
	
	//memory locations
	int c_current = 0;
	int c_proposed = 1;
	
	//heuristic indices
	int[] llhs_pert;
	int[] llhs_ls;
	
	//search process information
	int[] news;
	int[] durations;
	double[] evaluations;
	
	//variables holding solution qualities
	double e_proposed;
	double e_current;
	double e_run_best;
	double e_best;
	
	//variables related to acceptance
	double mu_impr;
	int n_impr;
	
	//variables related to restart
	long t_best;
	long t_run_start;
	long max_wait;
	long wait;	
	
	//FairShareILS using default parameter settings
	public FairShareILS(long seed) {
		super(seed);
	}
	
	//FairShareILS using temperature T
	public FairShareILS(long seed, double T) {
		this(seed);
		this.T = T;
	}

	@Override
	protected void solve(ProblemDomain problem) {
		//initialize search
		setup(problem);
		init();
		//ILS
		while(!hasTimeExpired()){
			long before = getElapsedTime();
			int option = select_option();
//			System.out.println("FairShare 70 "+option);
			apply_option(option);
			durations[option] += getElapsedTime()-before + 1;
			if(!problem.compareSolutions(c_proposed, c_current) && accept()){
				news[option]++;
				e_current = e_proposed;
				problem.copySolution(c_proposed, c_current);
			}
			//update evaluation (SpeedNew)
			evaluations[option] = (1.0 + news[option])/durations[option];
			if(restart()){
				//re-initialize search
				init();
			}
		}
	}
	
	private void setup(ProblemDomain problem){
		//initialize all variables initialized only once at the beginning of the optimization process
		llhs_ls = problem.getHeuristicsOfType(HeuristicType.LOCAL_SEARCH);
		int[] mut_llh = problem.getHeuristicsOfType(HeuristicType.MUTATION);
		int[] rc_llh = problem.getHeuristicsOfType(HeuristicType.RUIN_RECREATE);
		llhs_pert = new int[mut_llh.length+rc_llh.length];
		for(int i = 0; i < mut_llh.length;i++){
			llhs_pert[i] = mut_llh[i];
		}
		for(int i = 0; i < rc_llh.length;i++){
			llhs_pert[i+mut_llh.length] = rc_llh[i];
		}
		this.problem = problem;
		e_best = Double.MAX_VALUE;
		max_wait = 1;
		t_best = 0;
	}
	
	private void init(){
		//initialize all variables (re-)initialized every restart
		news = new int[llhs_pert.length+1];
		durations = new int[llhs_pert.length+1];
		evaluations = new double[llhs_pert.length+1];
		for(int i = 0; i < evaluations.length;i++){
			evaluations[i] = Double.MAX_VALUE/(llhs_pert.length+1);
		}
		mu_impr = 0;
		n_impr = 0;
		problem.initialiseSolution(c_current);
		e_current = problem.getFunctionValue(c_current);
		System.out.println("FairShare 117 quality of init "+e_current);
		e_run_best = e_current;
		wait = 0;
		t_run_start = getElapsedTime();
	}
	
	private int select_option(){
		//select an option proportional to its evaluation (RouletteWheel)
		//determine the norm
		double[] evaluations = this.evaluations;
		double norm = 0;
		for(int i = 0; i < evaluations.length;i++){
			norm += evaluations[i];
		}
		//select the option
		double p = rng.nextDouble()*norm;
		int selected = 0;
		double ac = evaluations[0];
		while(ac < p){
			selected++;
			ac += evaluations[selected];
		}
		return selected;
	}
	
	private void apply_option(int option){
		//apply the selected option (IteratedLocalSearch)
		//perturbation step
		if(option < llhs_pert.length){
			System.out.println("FairShare applying heuristic"+llhs_pert[option]);
			e_proposed = problem.applyHeuristic(llhs_pert[option],c_current,c_proposed);
			System.out.println("FairShare quality of heuristic: "+problem.getFunctionValue(c_proposed));
		}else{
			System.out.println("FairShare 149 False");
			problem.initialiseSolution(c_proposed);
			e_proposed = problem.getFunctionValue(c_proposed);
		}
		hasTimeExpired();
		//followed by local search
		localsearch();
		System.out.println("--------------end of applying heuristic-------------");
	}
	
	private void localsearch(){
		Vector<Integer> active = new Vector<Integer>();
		for(int i = 0; i < llhs_ls.length;i++){
			active.add(llhs_ls[i]);
		}
		while(!active.isEmpty()){
			int index = rng.nextInt(active.size());
			System.out.println("FairShare applying heuristic"+active.get(index));
			double e_temp = problem.applyHeuristic(active.get(index),c_proposed,c_proposed);
			System.out.println("FairShare quality of heuristic: "+problem.getFunctionValue(c_proposed));
			hasTimeExpired();
			if(e_temp < e_proposed){
				e_proposed = e_temp;
				active.clear();
				//restore
				for(int i = 0; i < llhs_ls.length;i++){
					active.add(llhs_ls[i]);
				}
			}else{
				active.remove(index);
			}
		}
	}
	
	private boolean accept(){
		//decides whether to accept c_proposed or not (AcceptProbabilisticWorse)
		if(e_proposed < e_current){
			n_impr++;
			mu_impr += (e_current-e_proposed-mu_impr)/n_impr;
		}
		return rng.nextDouble() < Math.exp((e_current-e_proposed)/(T*mu_impr));
	}
	
	private boolean restart(){
		//decides whether to restart the search process or not (RestartStuck)
		if(e_current < e_run_best){
			e_run_best = e_current;
			max_wait = Math.max(wait, max_wait);
			wait = 0;
			if(e_run_best < e_best){
				e_best = e_run_best;
				t_best = getElapsedTime()-t_run_start;
			}else if(e_run_best == e_best){
				t_best = Math.min(getElapsedTime()-t_run_start, t_best);
			}
		}else{
			wait++;
			double time_factor = (double)getTimeLimit()/getElapsedTime();
			double patience = max_wait*time_factor;
			if(max_wait != -1 && wait > patience && (getTimeLimit()-getElapsedTime()) >= t_best){
				return true;
			}
		}
		return false;
	}
	
	@Override
	public String toString() {
		return "FairShareILS(T:"+T+")";
	}

}
