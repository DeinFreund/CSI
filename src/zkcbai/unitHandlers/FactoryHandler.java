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
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.BuildTask;
import zkcbai.unitHandlers.units.tasks.Task;

/**
 *
 * @author User
 */
public class FactoryHandler extends UnitHandler {

    private static final String[] facs = new String[]{"factorycloak", "factoryplane"};

    private Set<UnitDef> builtFacs = new HashSet();

    public FactoryHandler(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
    }

    @Override
    public AIUnit addUnit(Unit u) {
        AIUnit au = new AIUnit(u, this);
        aiunits.put(u.getUnitId(), au);
        au.idle();
        builtFacs.add(u.getDef());
        return au;
    }

    @Override
    public void unitIdle(AIUnit u) {
        switch (u.getUnit().getDef().getName()) {
            case "factorycloak":
                u.assignTask(new BuildTask(clbk.getUnitDefByName("armpw"),u.getPos(),0,this,clbk,command));
                break;
        }
    }

    @Override
    public void abortedTask(Task t) {
    }

    @Override
    public void finishedTask(Task t) {
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
        if (!aiunits.containsKey(u.getUnit().getUnitId())) return;
        aiunits.remove(u.getUnit().getUnitId());
        builtFacs.clear();
        for (AIUnit au : aiunits.values()){
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

}
