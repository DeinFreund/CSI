/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import zkcbai.Command;
import zkcbai.helpers.ZoneManager.Area;
import zkcbai.unitHandlers.squads.RaiderSquad;
import zkcbai.unitHandlers.squads.ScoutSquad;
import zkcbai.unitHandlers.squads.Squad;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.Task;
import zkcbai.EnemyDiscoveredListener;
import zkcbai.EnemyEnterLOSListener;

/**
 *
 * @author User
 */
public class FighterHandler extends UnitHandler implements EnemyDiscoveredListener,EnemyEnterLOSListener {

    public FighterHandler(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        cmd.addEnemyDiscoveredListener(this);
        cmd.addEnemyEnterLOSListener(this);
    }

    Set<Squad> squads = new HashSet();

    Map<Integer, Squad> unitSquads = new TreeMap();

    @Override
    public AIUnit addUnit(Unit u) {
        AIUnit au = new AIUnit(u, this);
        aiunits.put(u.getUnitId(), au);

        useUnit(au);
        return au;
    }
    
    private void useUnit(AIUnit au){
        for (Squad s : squads) {
            if (s.size() < 5 && s.timeTo(au) < 30 * 10) {
                unitSquads.put(au.getUnit().getUnitId(), s);
                s.addUnit(au);
                return;
            }
        }
        Area a = command.areaManager.getArea(au.getPos()).getNearestArea(command.areaManager.HOSTILE);
        AIFloat3 target = null;
        if (a != null) {
            target = a.getPos();
        }

        Squad rs = new RaiderSquad(this, command, clbk, target);
        squads.add(rs);
        unitSquads.put(au.getUnit().getUnitId(), rs);
        rs.addUnit(au);
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

    public AIFloat3 requestNewTarget(RaiderSquad s) {
        Enemy best = null;
        AIFloat3 spos = s.getPos();
        Collection<Enemy> enemies = command.getEnemyUnitsIn(spos, 100000);
        float mindist = Float.MAX_VALUE;
        for (Enemy e : enemies) {
            if (e.isBuilding() && e.distanceTo(spos) < mindist) {
                mindist = e.distanceTo(spos);
                best = e;
            }
        }
        if (best != null) {
            return best.getPos();
        }

        Area a = command.areaManager.getArea(s.getPos()).getNearestArea(command.areaManager.HOSTILE);

        if (a != null) {
            return a.getPos();
        }

        int mintime = Integer.MAX_VALUE;
        for (Enemy e : enemies) {
            if (e.timeSinceLastSeen() < mintime && command.defenseManager.isRaiderAccessible(e.getPos())) {
                mintime = e.timeSinceLastSeen();
                best = e;
            }
        }
        if (best != null) {
            return best.getPos();
        }

        ScoutSquad ss = new ScoutSquad(this, command, clbk);
        for (AIUnit u : s.disband()) {
            ss.addUnit(u);
            unitSquads.remove(u.getUnit().getUnitId());
            unitSquads.put(u.getUnit().getUnitId(), ss);
            u.getUnit().stop((short) 0, command.getCurrentFrame());
        }
        squads.add(ss);

        return new AIFloat3();
    }

    public void squadDestroyed(Squad s) {
        squads.remove(s);
    }

    @Override
    public void reportSpam() {
        throw new RuntimeException("I spammed MoveTasks!");
    }

    @Override
    public void enemyDiscovered(Enemy e) {
        for (Squad s : squads){
            if (s instanceof ScoutSquad){
                for (AIUnit u : s.disband()) {
                    unitSquads.remove(u.getUnit().getUnitId());
                    useUnit(u);
                }
            }
        }
    }

    @Override
    public void enemyEnterLOS(Enemy e) {
        enemyDiscovered(e);
    }

}
