/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers;

import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import com.springrts.ai.oo.clb.UnitDef;
import java.util.HashSet;
import java.util.Set;
import zkcbai.Command;
import zkcbai.UpdateListener;
import zkcbai.unitHandlers.units.AISquad;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.BuildTask;
import zkcbai.unitHandlers.units.tasks.Task;

/**
 *
 * @author User
 */
public class FactoryHandler extends UnitHandler implements UpdateListener {

    private static final String[] facs = new String[]{"factorycloak", "factoryplane"};

    private Set<UnitDef> builtFacs = new HashSet();
    private int assaultRequests = 0;

    private final int assaultFrame = 10000;

    public FactoryHandler(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        //cmd.addSingleUpdateListener(this, assaultFrame);
    }

    @Override
    public AIUnit addUnit(Unit u) {
        AIUnit au = new AIUnit(u, this);
        aiunits.put(u.getUnitId(), au);
        builtFacs.add(u.getDef());
        return au;
    }

    @Override
    public void troopIdle(AIUnit u) {  
        switch (u.getUnit().getDef().getName()) {
            case "factorycloak":
                if (assaultRequests > 0) {
                    u.assignTask(new BuildTask(clbk.getUnitDefByName("armzeus"), u.getPos(), 0, this, clbk, command));
                } else {
                    u.assignTask(new BuildTask(clbk.getUnitDefByName("armpw"), u.getPos(), 0, this, clbk, command));
                }
                break;
        }
    }

    public void requestAssault(int amt) {
        assaultRequests += amt;
    }

    @Override
    public void abortedTask(Task t) {
    }

    @Override
    public void finishedTask(Task t) {
        if (t.getResult() instanceof AIUnit) {
            if (((AIUnit) t.getResult()).getType() == AIUnit.UnitType.assault) {
                assaultRequests--;
            }
        }
    }

    public UnitDef getNextFac() {
        for (String s : facs) {
            if (!builtFacs.contains(clbk.getUnitDefByName(s))) {
                return clbk.getUnitDefByName(s);
            }
        }
        return clbk.getUnitDefByName("factorycloak");
    }

    @Override
    public void removeUnit(AIUnit u) {
        if (!aiunits.containsKey(u.getUnit().getUnitId())) {
            return;
        }
        aiunits.remove(u.getUnit().getUnitId());
        builtFacs.clear();
        for (AIUnit au : aiunits.values()) {
            builtFacs.add(au.getUnit().getDef());
        }
    }

    @Override
    public void unitDestroyed(AIUnit u) {
        removeUnit(u);
    }

    @Override
    public void unitDestroyed(Enemy e) {
    }

    @Override
    public void reportSpam() {
        throw new RuntimeException("I spammed MoveTasks!");
    }

    @Override
    public void troopIdle(AISquad s) {
    }

    @Override
    public void update(int frame) {
            requestAssault(6);
    }

}
