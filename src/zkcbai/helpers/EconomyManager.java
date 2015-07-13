/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.helpers;

import com.springrts.ai.oo.clb.OOAICallback;
import zkcbai.Command;
import zkcbai.unitHandlers.units.AIUnit;

/**
 *
 * @author User
 */
public class EconomyManager extends Helper{

    final float ENERGY = 0.3f;
    final float OVERDRIVE = 0.05f;
    final float DEFENSE = 0.2f;
    final float OFFENSE = 0.5f;
    
    float generosity = 0;
    float adaption = 3e-3f;
    
    float energy = 0;
    float offense = 0;
    float defense = 0;
    float overdrive = 0;
    
    public void useEnergyBudget(float amt){
        energy += amt;
    }
    
    public float getRemainingEnergyBudget(){
        return  ENERGY * (totalMetal + generosity) - energy;
    }
    
    public void useOffenseBudget(float amt){
        offense += amt;
    }
    
    public float getRemainingOffenseBudget(){
        return  OFFENSE * (totalMetal + generosity) - offense;
    }
    
    public void useDefenseBudget(float amt){
        defense += amt;
    }
    
    public float getRemainingDefenseBudget(){
        return  DEFENSE * (totalMetal + generosity) - defense;
    }
    
    public void useOverdriveBudget(float amt){
        overdrive += amt;
    }
    
    public float getRemainingOverdriveBudget(){
        return  OVERDRIVE * (totalMetal + generosity) - overdrive;
    }
    
    public EconomyManager(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        totalMetal = clbk.getEconomy().getCurrent(command.metal);
    }

    @Override
    public void unitFinished(AIUnit u) {
    }
    
    public float getTotalMetal(){
        return totalMetal;
    }
    
    float totalMetal = 500;

    int lastFrame = 0;
    
    @Override
    public void update(int frame) {
        totalMetal += clbk.getEconomy().getIncome(command.metal) / 30f * (frame - lastFrame);
        lastFrame = frame;
        if (frame % 200 == 0){
            command.debug("Current generosity: " + generosity);
        }
        float mid = 50;
        generosity += Math.signum(clbk.getEconomy().getCurrent(command.metal) - mid) * 
                adaption * Math.pow(Math.abs(Math.min(mid*2,clbk.getEconomy().getCurrent(command.metal)) - mid), 2.2)/ 20f;
        
    }
    
}
