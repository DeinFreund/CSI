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
import zkcbai.UnitFinishedListener;
import zkcbai.UpdateListener;
import zkcbai.helpers.ZoneManager;
import zkcbai.helpers.ZoneManager.Area;
import zkcbai.helpers.ZoneManager.Zone;
import zkcbai.unitHandlers.betterSquads.AvengerSquad;
import zkcbai.unitHandlers.units.AISquad;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.MoveTask;
import zkcbai.unitHandlers.units.tasks.Task;
import zkcbai.unitHandlers.units.tasks.WaitTask;
import zkcbai.utility.Pair;

/**
 *
 * @author User
 */
public class AvengerHandler extends UnitHandler implements UpdateListener, EnemyDiscoveredListener, EnemyEnterLOSListener, UnitFinishedListener {

    protected AISquad fighters = new AISquad(this);
    protected Set<AIUnit> scouts = new HashSet();
    protected float fighterDPS = 0;
    protected Set<Enemy> enemyAir = new HashSet();
    protected Set<Enemy> longRangeAA = new HashSet();
    public static final Set<UnitDef> AADefs = new HashSet();
    private String[] AADefNames = new String[]{"screamer", "armcir"};
    private Set<Area> scoutPos = new HashSet();
    private Map<AIUnit, Area> scoutTasks = new HashMap();
    private Set<AIUnit> repairing = new HashSet();
    private Set<AIUnit> friendlyAir = new HashSet();

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
        command.addUnitFinishedListener(this);
    }

    @Override
    public AIUnit addUnit(Unit u) {
        AIUnit au = new AIUnit(u, this);
        aiunits.put(u.getUnitId(), au);
        if (AvengerSquad.fighters.contains(u.getDef())) {
            fighters.addUnit(au);
            fighterDPS += au.getDPS();
            au.setRetreat(AIUnit.RetreatState.Retreat65);
            au.setLandWhenIdle(false);
            au.setAutoRepair(false);
        } else {
            scouts.add(au);
        }

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
        if (command.getCurrentFrame() - pos.getLastVisible() > 30 * 60) {
            for (Area a : scoutPos) {
                if (a.distanceTo(pos.getPos()) < 800) {
                    return;
                }
            }
            command.mark(pos.getPos(), "requested scout");
            scoutPos.add((pos));
        }
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

        if (t instanceof MoveTask) {
            command.mark(((MoveTask) t).getLastExecutingUnit().getPos(), "failed movetask, debug");
            finishedTask(t);
        }
    }

    @Override
    public void finishedTask(Task t) {
        if (t instanceof MoveTask && t.getInfo().contains("scout")) {
            MoveTask mt = (MoveTask) t;
            if (AvengerSquad.fighters.contains(mt.getLastExecutingUnit().getDef())) {
                fighters.addUnit((AIUnit) mt.getLastExecutingUnit());
            } else {
                scouts.add((AIUnit) mt.getLastExecutingUnit());
            }
            command.mark(scoutTasks.get((AIUnit) mt.getLastExecutingUnit()).getPos(), "scouted");
            if (scoutTasks.remove((AIUnit) mt.getLastExecutingUnit()) == null){
                command.debug("finished a non-existing scout task");
                command.debugStackTrace();
            }
        }
    }
    
    public Collection<AIUnit> getRepairingAvengers(){
        return repairing;
    }

    @Override
    public void reportSpam() {
        throw new AssertionError("Endless recursion");
    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {
        enemyAir.remove(e);
        longRangeAA.remove(e);
    }

    @Override
    public void unitDestroyed(AIUnit au, Enemy killer) {
        super.unitDestroyed(au, killer);
        friendlyAir.remove(au);
        scouts.remove(au);
    }

    @Override
    public boolean retreatForRepairs(AITroop u) {
        return false;
    }

    @Override
    public void update(int frame) {
        if (frame % 50 == 4) {
            for (AIUnit au : fighters.getUnits().toArray(new AIUnit[fighters.getUnits().size()])) {
                if (au.getUnit().getHealth() < 0.66 * au.getDef().getHealth()) {
                    repairing.add(au);
                    fighters.removeUnit(au, this);
                    fighterDPS -= au.getDPS();
                    au.assignTask(new WaitTask(command.getCurrentFrame() + 100, this));
                    au.moveTo(command.getStartPos(), command.getCurrentFrame() + 100);
                }
            }
            for (AIUnit au : repairing.toArray(new AIUnit[repairing.size()])) {
                if (au.getUnit().getHealth() > 0.99 * au.getDef().getHealth()) {
                    fighters.addUnit(au);
                    repairing.remove(au);
                    fighterDPS += au.getDPS();
                    command.mark(au.getPos(), "fighter repaired");
                }
            }
            if (fighters.getUnits().isEmpty()) {
                if (!repairing.isEmpty()) {
                    command.debug("All fighters repairing!");
                }
                return;
            }
            Set<AIUnit> usableScouts = new HashSet();
            usableScouts.addAll(scouts);
            usableScouts.addAll(fighters.getUnits());
            TreeMap<Float, Pair<AIUnit, Area>> dists = new TreeMap();
            for (Area a : scoutPos.toArray(new Area[scoutPos.size()])) {
                for (AIUnit fighter : usableScouts) {
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
                if (command.getCurrentFrame() - entry.getSecond().getLastVisible() <= 200) {
                    scoutPos.remove(entry.getSecond());
                    continue;
                }
                entry.getFirst().assignTask(new MoveTask(entry.getSecond().getPos(), Integer.MAX_VALUE, this, command.pathfinder.AVOID_ANTIAIR, command).setInfo("scout"));
                fighters.removeUnit(entry.getFirst(), this);
                scouts.remove(entry.getFirst());
                scoutTasks.put(entry.getFirst(), entry.getSecond());
                scoutPos.remove(entry.getSecond());
                if (fighters.getUnits().isEmpty()) {
                    return;
                }
            }
            for (AIUnit au : friendlyAir) {
                if (aiunits.containsKey(au.getUnit().getUnitId())) {
                    continue;
                }
                if (au.getArea().getZone() == Zone.hostile && au.getArea().getEnemyAADPS() * 3 > fighterDPS) {
                    continue;
                }
                if (au.getArea().getEnemyAADPS() < 200 || (au.getDef().equals(command.getDropHandler().VALK))) {
                    continue;
                }

                command.debug("avengers supporting " + au.getDef().getHumanName() + " because aadps: " + au.getArea().getEnemyAADPS());
                if (fighters.distanceTo(au.getPos()) < 700) {
                    fighters.fight(au.getPos(), command.getCurrentFrame() + 100);
                } else {
                    fighters.moveTo(au.getPos(), command.getCurrentFrame() + 100);//fighters.assignTask(new MoveTask(au.getPos(), command.getCurrentFrame() + 50, this, command.pathfinder.AVOID_ANTIAIR, command));
                }
                return;
            }
            for (Enemy e : enemyAir) { // enemies in friendly territorry
                Area area = command.areaManager.getArea(e.getPos());
                if ((area.getNearbyEnemies().size() < 4 || area.getZone() != Zone.hostile) && area.getEnemyAADPS() * 1.8 < fighterDPS) {
                    if (fighters.distanceTo(e.getPos()) < 1000) {
                        fighters.attack(e, command.getCurrentFrame() + 100);
                        command.debug(fighters.getUnits().size() + " avengers attacking " + e.getDef().getHumanName());
                    } else {
                        command.debug(fighters.getUnits().size() + " avengers intercepting " + e.getDef().getHumanName());
                        fighters.moveTo(e.getPos(), command.getCurrentFrame() + 100);
                    }
                    return;
                }
            }
            Enemy best = null;
            for (Enemy e : command.areaManager.getEnemyUnitsInAreas(command.areaManager.FRIENDLY)) {
                if (best == null || best.getHealth() > e.getHealth() || (!best.isAntiAir() && best.getDef().isAbleToCloak())) {
                    best = e;
                }
            }
            if (best != null) {
                fighters.attack(best, command.getCurrentFrame() + 100);
                command.debug(fighters.getUnits().size() + " avengers attacking " + best.getDef().getHumanName());
                return;
            }
            if (frame < 30 * 60 * 8) {
                for (Enemy e : enemyAir) {
                    Area area = command.areaManager.getArea(e.getPos());
                    if ((area.getEnemyAADPS() + 100) * 4 < fighterDPS) {
                        if (area.getEnemyAADPS() < 1) {
                            area.updateAADPS();
                        }
                        if (fighters.distanceTo(e.getPos()) < 1000) {
                            fighters.attack(e, command.getCurrentFrame() + 100);
                            command.debug(fighters.getUnits().size() + " avengers attacking " + e.getDef().getHumanName() + " in enemy territory where"
                                    + area.getEnemyAADPS() + " * 8 < " + fighterDPS);
                        } else {
                            command.debug(fighters.getUnits().size() + " avengers intercepting " + e.getDef().getHumanName() + " in enemy territory where"
                                    + area.getEnemyAADPS() + " * 8 < " + fighterDPS);
                            fighters.moveTo(e.getPos(), command.getCurrentFrame() + 100);
                        }
                        return;
                    }
                }
            }

            fighters.moveTo(fighters.getArea().getNearestArea(command.areaManager.SAFE).getPos(), command.getCurrentFrame() + 100);
            command.debug("no target for avengers");
        }
    }

    public Collection<Enemy> getEnemyLongRangeAntiAir() {
        return longRangeAA;
    }

    public Collection<Enemy> getEnemyAir() {
        return enemyAir;
    }

    public float getAvengerDPS(){
        return fighterDPS;
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
        if (e.getDef().isAbleToFly() && e.getDef().getCost(command.metal) > 80) {
            enemyAir.add(e);
        } else {
            enemyAir.remove(e);
        }
        if (AADefs.contains(e.getDef())) {
            longRangeAA.add(e);
        }
    }

    @Override
    public void unitFinished(AIUnit u) {
        if (u.getDef().isAbleToFly()) {
            friendlyAir.add(u);
        }
    }
}
