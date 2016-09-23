/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.helpers;

import com.springrts.ai.oo.clb.OOAICallback;
import java.util.HashMap;
import java.util.Map;
import zkcbai.Command;
import zkcbai.unitHandlers.units.AIUnit;

/**
 *
 * @author User
 */
public class EconomyManager extends Helper {

    float generosity = 0;
    float adaption = 5e-2f;

    Map<Budget, Float> fraction = new HashMap(); //all fractions should add to 1
    Map<Budget, Float> used = new HashMap();

    public static enum Budget {
        economy, defense, offense
    }

    public EconomyManager(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        totalMetal = clbk.getEconomy().getCurrent(command.metal);
        fraction.put(Budget.economy, 0.4f);
        fraction.put(Budget.defense, 0.2f);
        fraction.put(Budget.offense, 0.4f);
        used.put(Budget.economy, 0f);
        used.put(Budget.defense, 0f);
        used.put(Budget.offense, 0f);
    }

    public void useBudget(Budget budget, float amt) {
        if (getRemainingBudget(budget) < 0) {
            command.debug("Warning: " + budget.name() + " budget overdrawn by " + amt + " at ");
            command.debugStackTrace();
        }
        used.put(budget, used.get(budget) + amt);
    }

    public float getRemainingBudget(Budget budget) {
        return fraction.get(budget) * (totalMetal + generosity) - used.get(budget);
    }

    @Override
    public void unitFinished(AIUnit u) {
    }

    public float getTotalMetal() {
        return totalMetal;
    }

    float totalMetal = 500;

    int lastFrame = 0;

    @Override
    public void update(int frame) {
        if (frame < 50) {
            int coms = command.getCommanderHandlers().size();
            totalMetal = coms * 500;

            if (frame == 49) {

                used.put(Budget.economy, used.get(Budget.economy) - coms * 400f);
                used.put(Budget.defense, used.get(Budget.defense) - coms * 50f);
                used.put(Budget.offense, used.get(Budget.offense) - coms * 150f);
                command.debug("Accounting for " + command.getCommanderHandlers().size() + " commanders' start income.");
            }
        }
        if (frame > 90) {
            totalMetal += (command.getBuilderHandler().getMetalIncome()) / 30f * (frame - lastFrame);
        }
        lastFrame = frame;
        if (frame % 100 == 2) {
            command.debug("Economy budget: " + getRemainingBudget(Budget.economy));
            command.debug("Offense budget: " + getRemainingBudget(Budget.offense));
            command.debug("Defense budget: " + getRemainingBudget(Budget.defense));
        }
        if (frame > 30 * 30) {
            float mid = 0.33f * command.getBuilderHandler().getMetalStorage();
            generosity += Math.signum(clbk.getEconomy().getCurrent(command.metal) - mid)
                    * adaption * Math.pow(Math.abs(Math.min(mid * 2, clbk.getEconomy().getCurrent(command.metal)) - mid), 1.7) / 20f;
        }

    }

}
