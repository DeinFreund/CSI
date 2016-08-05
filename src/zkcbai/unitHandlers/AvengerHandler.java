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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import zkcbai.Command;
import zkcbai.EnemyDiscoveredListener;
import zkcbai.EnemyEnterLOSListener;
import zkcbai.UpdateListener;
import zkcbai.helpers.ZoneManager.Area;
import zkcbai.unitHandlers.units.AISquad;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.MoveTask;
import zkcbai.unitHandlers.units.tasks.Task;
import zkcbai.utility.Pair;

/**
 *
 * @author User
 */
public class AvengerHandler extends UnitHandler implements UpdateListener, EnemyDiscoveredListener, EnemyEnterLOSListener {

    protected AISquad fighters = new AISquad(this);
    protected float fighterDPS = 0;
    protected Set<Enemy> enemyAir = new HashSet();
    protected Set<Enemy> longRangeAA = new HashSet();
    public static final Set<UnitDef> AADefs = new HashSet();
    private String[] AADefNames = new String[]{"screamer", "armcir"};
    private Set<Area> scoutPos = new HashSet();
    private Map<AIUnit, Area> scoutTasks = new HashMap();
    private Set<AIUnit> repairing = new HashSet();

    public AvengerHandler(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        cmd.addUpdateListener(this);
        for (String s : AADefNames) {
            AADefs.add(clbk.getUnitDefByName(s));
        }
        for (AIFloat3 startpos : cmd.areaManager.getEnemyStartPositions()) {
            requestScout(command.areaManager.getArea(startpos));
        }
        command.addEnemyDiscoveredListener(this);
        command.addEnemyEnterLOSListener(this);
    }

    @Override
    public AIUnit addUnit(Unit u) {
        AIUnit au = new AIUnit(u, this);
        aiunits.put(u.getUnitId(), au);
        fighters.addUnit(au);
        fighterDPS += au.getDPS();
        au.setRetreat(AIUnit.RetreatState.Retreat65);
        au.setLandWhenIdle(false);
        au.setAutoRepair(false);

        troopIdle(au);
        return au;
    }

    @Override
    public void addUnit(AIUnit au) {
        aiunits.put(au.getUnit().getUnitId(), au);
        fighters.addUnit(au);
        fighterDPS += au.getDPS();
        au.setRetreat(AIUnit.RetreatState.Retreat65);
        au.setLandWhenIdle(false);
        au.setAutoRepair(false);

        troopIdle(au);
    }

    @Override
    public void removeUnit(AIUnit u) {
        if (scoutTasks.containsKey(u)) {
            scoutPos.add(scoutTasks.get(u));
            scoutTasks.remove(u);
        }
        aiunits.remove(u.getUnit().getUnitId());
        if (fighters.getUnits().contains(u)) {
            fighters.removeUnit(u, new DevNullHandler(command, clbk));
            fighterDPS -= u.getDPS();
        }
        repairing.remove(u);
    }

    public void requestScout(Area pos) {
        scoutPos.add((pos));
    }

    @Override
    public void troopIdle(AIUnit u) {
        u.wait(command.getCurrentFrame() + 30);

    }

    @Override
    public void troopIdle(AISquad s) {
        s.wait(command.getCurrentFrame() + 30);
    }

    @Override
    public void abortedTask(Task t) {

    }

    @Override
    public void finishedTask(Task t) {
        if (t instanceof MoveTask) {
            MoveTask mt = (MoveTask) t;
            fighters.addUnit((AIUnit) mt.getLastExecutingUnit());
            command.mark(scoutTasks.get((AIUnit) mt.getLastExecutingUnit()).getPos(), "scouted");
            scoutTasks.remove((AIUnit) mt.getLastExecutingUnit());
        }
    }

    @Override
    public void reportSpam() {
        throw new AssertionError("Endless recursion");
    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {
        enemyAir.remove(e);
    }

    @Override
    public boolean retreatForRepairs(AITroop u) {
        return false;
    }

    @Override
    public void update(int frame) {
        if (frame % 50 == 4 && !fighters.getUnits().isEmpty()) {
            for (AIUnit au : fighters.getUnits().toArray(new AIUnit[fighters.getUnits().size()])) {
                if (au.getUnit().getHealth() < 0.66 * au.getDef().getHealth()) {
                    repairing.add(au);
                    fighters.removeUnit(au, this);
                    fighterDPS -= au.getDPS();
                }
            }
            for (AIUnit au : repairing.toArray(new AIUnit[repairing.size()])) {
                if (au.getUnit().getHealth() > 0.99 * au.getDef().getHealth()) {
                    fighters.addUnit(au);
                    repairing.remove(au);
                    fighterDPS += au.getDPS();
                }
            }
            if (fighters.getUnits().isEmpty()) {
                return;
            }
            TreeMap<Float, Pair<AIUnit, Area>> dists = new TreeMap();
            for (Area a : scoutPos.toArray(new Area[scoutPos.size()])) {
                for (AIUnit fighter : fighters.getUnits()) {
                    dists.put(fighter.distanceTo(a.getPos()), new Pair(fighter, a));
                }
            }
            for (Pair<AIUnit, Area> entry : dists.values()) {
                if (!fighters.getUnits().contains(entry.getFirst())) {
                    continue;
                }
                if (!scoutPos.contains(entry.getSecond())) {
                    continue;
                }
                entry.getFirst().assignTask(new MoveTask(entry.getSecond().getPos(), Integer.MAX_VALUE, this, command.pathfinder.AVOID_ANTIAIR, command));
                fighters.removeUnit(entry.getFirst(), this);
                scoutTasks.put(entry.getFirst(), entry.getSecond());
                scoutPos.remove(entry.getSecond());
                if (fighters.getUnits().isEmpty()) {
                    return;
                }
            }
            for (Enemy e : enemyAir) {
                if (command.areaManager.getArea(e.getPos()).getAADPS() * 10 < fighterDPS) {
                    if (fighters.distanceTo(e.getPos()) < 1000) {
                        fighters.attack(e, command.getCurrentFrame() + 100);
                    } else {
                        fighters.assignTask(new MoveTask(e.getPos(), Integer.MAX_VALUE, this, command.pathfinder.AVOID_ANTIAIR, command));
                    }
                    command.debug("avengers attacking " + e.getDef().getHumanName());
                    return;
                } else {
                    command.debug(e.getDef().getHumanName() + " is too well protected by antiair");
                }
            }
            Enemy best = null;
            for (Enemy e : command.areaManager.getEnemyUnitsInAreas(command.areaManager.FRIENDLY)) {
                if (best == null || best.getHealth() > e.getHealth()) {
                    best = e;
                }
            }
            if (best != null) {
                fighters.attack(best, command.getCurrentFrame() + 100);
                command.debug("avengers attacking " + best.getDef().getHumanName());
                return;
            }
            command.debug("no target for avengers");
        }
    }

    public Collection<Enemy> getEnemyLongRangeAntiAir() {
        return longRangeAA;
    }

    @Override
    public void enemyDiscovered(Enemy e) {
        if (e.getPos().y - clbk.getMap().getElevationAt(e.getPos().x, e.getPos().z) > 38) {
            command.mark(e.getPos(), "Hostile aircraft assumed");
            enemyAir.add(e);
        }
    }

    @Override
    public void enemyEnterLOS(Enemy e) {
        if (e.getDef().isAbleToFly()) {
            enemyAir.add(e);
        } else {
            enemyAir.remove(e);
        }
        if (AADefs.contains(e.getDef())) {
            longRangeAA.add(e);
        }
    }
}
