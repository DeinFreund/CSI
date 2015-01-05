/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers;

import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import zkcbai.Command;
import zkcbai.unitHandlers.squads.RaiderSquad;
import zkcbai.unitHandlers.squads.Squad;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.Task;

/**
 *
 * @author User
 */
public class FighterHandler extends UnitHandler {

    public FighterHandler(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
    }

    Set<Squad> squads = new HashSet();

    Map<Integer, Squad> unitSquads = new TreeMap();

    @Override
    public AIUnit addUnit(Unit u) {
        AIUnit au = new AIUnit(u, this);
        aiunits.put(u.getUnitId(), au);

        for (Squad s : squads) {
            if (s.size() < 5 && s.timeTo(au) < 30 * 10) {
                unitSquads.put(u.getUnitId(), s);
                s.addUnit(au);
                return au;
            }
        }
        Squad rs = new RaiderSquad(this, command, clbk, command.areaManager.getArea(u.getPos()).getNearestArea(command.areaManager.HOSTILE).getPos());
        squads.add(rs);
        unitSquads.put(u.getUnitId(), rs);
        rs.addUnit(au);
        return au;
    }

    @Override
    public void unitIdle(AIUnit u) {
        if (!unitSquads.containsKey(u.getUnit().getUnitId())) {
            return;
        }
        //command.mark(u.getPos(), "idle");
        unitSquads.get(u.getUnit().getUnitId()).unitIdle(u);
    }

    @Override
    public void abortedTask(Task t) {

    }

    @Override
    public void finishedTask(Task t) {
    }

    @Override
    public void removeUnit(AIUnit u) {
        if (!aiunits.containsKey(u.getUnit().getUnitId())) {
            return;
        }
        aiunits.remove(u.getUnit().getUnitId());
        unitSquads.get(u.getUnit().getUnitId()).removeUnit(u);
        unitSquads.remove(u.getUnit().getUnitId());
    }

    @Override
    public void unitDestroyed(AIUnit u) {
        removeUnit(u);
    }

    @Override
    public void unitDestroyed(Enemy e) {
    }
    
    public void squadDestroyed(Squad s){
        squads.remove(s);
    }

}
