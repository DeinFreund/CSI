/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import com.springrts.ai.oo.clb.UnitDef;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import zkcbai.Command;
import zkcbai.helpers.ZoneManager.Area;
import zkcbai.unitHandlers.squads.RaiderSquad;
import zkcbai.unitHandlers.squads.ScoutSquad;
import zkcbai.unitHandlers.squads.SquadHandler;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.Task;
import zkcbai.EnemyDiscoveredListener;
import zkcbai.EnemyEnterLOSListener;
import zkcbai.unitHandlers.squads.AssaultSquad;
import zkcbai.unitHandlers.units.AISquad;

/**
 *
 * @author User
 */
public class FighterHandler extends UnitHandler implements EnemyDiscoveredListener, EnemyEnterLOSListener {

    public FighterHandler(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        cmd.addEnemyDiscoveredListener(this);
        cmd.addEnemyEnterLOSListener(this);

    }

    Set<SquadHandler> squads = new HashSet();

    Map<Integer, SquadHandler> unitSquads = new TreeMap();

    @Override
    public AIUnit addUnit(Unit u) {
        AIUnit au = new AIUnit(u, this);
        aiunits.put(u.getUnitId(), au);

        useUnit(au);
        return au;
    }

    private void useUnit(AIUnit au) {
        Area a;
        AIFloat3 target;
        SquadHandler rs;
        switch (au.getType()) {
            case raider:
                for (SquadHandler s : squads) {
                    if (s instanceof RaiderSquad && s.size() < 5 && s.timeTo(au) < 30 * 10) {
                        unitSquads.put(au.getUnit().getUnitId(), s);
                        s.addUnit(au);
                        return;
                    }
                }
                a = command.areaManager.getArea(au.getPos()).getNearestArea(command.areaManager.HOSTILE);
                target = null;
                if (a != null) {
                    target = a.getPos();
                }

                rs = new RaiderSquad(this, command, clbk, target);
                squads.add(rs);
                unitSquads.put(au.getUnit().getUnitId(), rs);
                rs.addUnit(au);
                break;
            case assault:
                for (SquadHandler s : squads) {
                    if (s instanceof AssaultSquad && s.size() < 6 && s.timeTo(au) < 30 * 10) {
                        unitSquads.put(au.getUnit().getUnitId(), s);
                        s.addUnit(au);
                        return;
                    }
                }
                a = command.areaManager.getArea(au.getPos()).getNearestArea(command.areaManager.FORTIFIED);
                target = null;
                if (a != null) {
                    target = a.getPos();
                }

                rs = new AssaultSquad(this, command, clbk, target);
                squads.add(rs);
                unitSquads.put(au.getUnit().getUnitId(), rs);
                rs.addUnit(au);
                break;
        }
    }

    @Override
    public void troopIdle(AIUnit u) {
        throw new RuntimeException("FighterHandler didn't dispatch unit to SquadHandler");
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
            if (e.isBuilding() && e.distanceTo(spos) < mindist && command.defenseManager.isRaiderAccessible(e.getPos())) {
                mindist = e.distanceTo(spos);
                best = e;
            }
        }
        if (best != null) {
            return best.getPos();
        }
        
        Area a = command.areaManager.getArea(s.getPos()).getNearestArea(command.areaManager.HOSTILE_RAIDER_ACCESSIBLE);
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

        command.getFactoryHandler().requestAssault(5);

        return new AIFloat3();
    }

    public AIFloat3 requestNewTarget(AssaultSquad s) {

        Area a = command.areaManager.getArea(s.getPos()).getNearestArea(command.areaManager.FORTIFIED_ASSAULT_ACCESSIBLE);
        if (a != null) {
            return a.getPos();
        }

        Enemy best = null;
        AIFloat3 spos = s.getPos();
        Collection<Enemy> enemies = command.getEnemyUnitsIn(spos, 100000);
        float mindist = Float.MAX_VALUE;
        for (Enemy e : enemies) {
            if (e.isBuilding() && e.distanceTo(spos) < mindist && command.defenseManager.isAssaultAccessible(e.getPos())
                    && command.defenseManager.getImmediateDanger(e.getPos()) > 0) {
                mindist = e.distanceTo(spos);
                best = e;
            }
        }
        if (best != null) {
            return best.getPos();
        }

        int mintime = Integer.MAX_VALUE;
        for (Enemy e : enemies) {
            if (e.timeSinceLastSeen() < mintime && command.defenseManager.isAssaultAccessible(e.getPos())
                    && command.defenseManager.getImmediateDanger(e.getPos()) > 0) {
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

    public void squadDestroyed(SquadHandler s) {
        squads.remove(s);
    }

    @Override
    public void reportSpam() {
        throw new RuntimeException("I spammed MoveTasks!");
    }

    @Override
    public void enemyDiscovered(Enemy e) {
        for (SquadHandler s : squads) {
            if (s instanceof ScoutSquad) {
                for (AIUnit u : s.disband()) {
                    unitSquads.remove(u.getUnit().getUnitId());
                    useUnit(u);
                }
            }
        }
    }

    public void requestReinforcements(SquadHandler rs) {
        float mindist = Float.MAX_VALUE;
        AIUnit best = null;
        SquadHandler bests = null;
        for (SquadHandler s : squads) {
            if (s.equals(rs)) {
                continue;
            }
            for (AIUnit u : s.getUnits()) {
                if (u.getType() == rs.getType() && u.distanceTo(rs.getPos()) < mindist) {
                    mindist = u.distanceTo(rs.getPos());
                    best = u;
                    bests = s;
                }
            }
        }
        if (best != null) {
            bests.removeUnit(best);
            unitSquads.remove(best.getUnit().getUnitId());
            unitSquads.put(best.getUnit().getUnitId(), rs);
            rs.addUnit(best);
        }
    }

    @Override
    public void enemyEnterLOS(Enemy e) {
        enemyDiscovered(e);
    }

    @Override
    public void troopIdle(AISquad s) {
    }

}
