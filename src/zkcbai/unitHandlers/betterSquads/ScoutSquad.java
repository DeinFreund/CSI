/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.betterSquads;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.UnitDef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import zkcbai.Command;
import zkcbai.EnemyEnterLOSListener;
import zkcbai.helpers.AreaChecker;
import zkcbai.helpers.ZoneManager.Area;
import zkcbai.helpers.ZoneManager.Mex;
import zkcbai.unitHandlers.FighterHandler;
import zkcbai.unitHandlers.units.AISquad;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.AttackTask;
import zkcbai.unitHandlers.units.tasks.MoveTask;
import zkcbai.unitHandlers.units.tasks.Task;

/**
 *
 * @author User
 */
public class ScoutSquad extends SquadManager implements EnemyEnterLOSListener {

    public ScoutSquad(FighterHandler fighterHandler, final Command command, OOAICallback callback) {
        this(fighterHandler, command, callback, null);
        dangerChecker = new AreaChecker() {

            @Override
            public boolean checkArea(Area a) {
                return command.pathfinder.AVOID_ENEMIES.getCost(a) < 0.001;
            }
        };
    }

    public ScoutSquad(FighterHandler fighterHandler, Command command, OOAICallback callback, Collection<UnitDef> availableUnits) {
        super(fighterHandler, command, callback, availableUnits);
        for (String s : scoutIds) {
            scouts.add(command.getCallback().getUnitDefByName(s));
        }
        command.addEnemyEnterLOSListener(this);

    }

    final static private String[] scoutIds = {"armpw", "corak", "corfav", "blastwing", "amphraider3", "shipscout", "corsh", "armflea", "puppy"};

    final static public Set<UnitDef> scouts = new HashSet();

    private AISquad aisquad;

    private static int lastScoutSquad = -10000;

    @Override
    public List<UnitDef> getRequiredUnits(Collection<UnitDef> availableUnits) {
        int reqcnt = 1;
        for (AIUnit au : units) {
            if (scouts.contains(au.getDef())) {
                reqcnt--;
            }
        }
        if (this.aisquad != null && units.size() != aisquad.getUnits().size()) {
            throw new AssertionError("didnt add all units correctly to scoutsquad " + aisquad.getUnits().size() + " / " + units.size());
        }
        Set<UnitDef> unitset = new HashSet(availableUnits);
        for (UnitDef ud : scouts) {
            if (unitset.contains(ud)) {
                List<UnitDef> req = new ArrayList();
                for (int i = 0; i < reqcnt; i++) {
                    req.add(ud);
                }
                return req;
            }
        }
        /*command.debug("Warning(ScoutSquad): Couldn't find any required units in set of available Units: ");
        for (UnitDef ud : availableUnits) {
            command.debug(ud.getHumanName());
        }*/
        return null;
    }

    @Override
    public float getUsefulness() {
        if (command.getFactoryHandler().getBuildOptions().contains(clbk.getUnitDefByName("fighter"))) {
            return 0;
        }
        if (command.getAvengerHandler().getUnits().isEmpty()) return 0.91f;
        return 0f;
    }

    @Override
    public AIUnit.UnitType getType() {
        return AIUnit.UnitType.raider;
    }

    @Override
    public void troopIdle(AIUnit u) {
        troopIdle((AITroop) u);
    }

    @Override
    public void troopIdle(AISquad s) {
        troopIdle((AITroop) s);
    }

    @Override
    public void addUnit(AIUnit u) {
        super.addUnit(u);
        if (aisquad == null) {
            aisquad = new AISquad(this);
        }
        aisquad.addUnit(u);
        command.debug("added " + u.getDef().getHumanName() + " to scoutsquad, size is now " + aisquad.getUnits().size());
    }

    @Override
    public Set<AIUnit> disband() {
        for (AIUnit au : aisquad.getUnits()) {
            aisquad.removeUnit(au, this);
        }
        command.removeEnemyEnterLOSListener(this);
        return super.disband();
    }

    boolean finished = false;
    Enemy target = null;

    Area targetArea = null;

    public static AreaChecker dangerChecker;

    @Override
    public void troopIdle(AITroop t) {
        if (!getRequiredUnits(availableUnits).isEmpty() && !finished) {
            t.wait(command.getCurrentFrame() + 60);
            return;
        } else {
            finished = true;
        }
        if (t instanceof AIUnit) {
            throw new AssertionError("all units should be in aisquad instead of single aiunits");
        } else {
            //command.debug("scoutsquad has " + ((AISquad)t).getUnits().size() + " units");
        }

        Set<Area> reachableAreas = command.areaManager.getArea(t.getPos()).getConnectedAreas(t.getMovementType(), dangerChecker);
        if (reachableAreas.isEmpty()) {
            AIFloat3 evadepos = command.areaManager.getArea(t.getPos()).getNearestArea(dangerChecker).getPos();
            t.moveTo(evadepos, command.getCurrentFrame() + 70);
            t.assignTask(new MoveTask(evadepos, command.getCurrentFrame() + 70, this, command.pathfinder.AVOID_ENEMIES, command));
            //command.debug("ESCAPING!");
            return;
        } else {
            //command.debug("safe");
        }
        if (target != null && reachableAreas.contains(command.areaManager.getArea(target.getPos()))) {
            if (t.distanceTo(target.getPos()) > Math.max(500, t.getMaxRange())) {
                t.assignTask(new MoveTask(target.getPos(), command.getCurrentFrame() + 70, this, command.pathfinder.AVOID_ENEMIES, command));
            } else {
                t.assignTask(new AttackTask(target, command.getCurrentFrame() + 80, this, command));
            }
            return;
        }
        if (targetArea != null && reachableAreas.contains(targetArea) && !command.areaManager.getArea(t.getPos()).equals(targetArea)) {

            t.assignTask(new MoveTask(targetArea.getPos(), command.getCurrentFrame() + 70, this, command.pathfinder.AVOID_ENEMIES, command));
            return;
        }
        Enemy best = null;
        for (Mex m : command.areaManager.getMexes()) {
            if (best == null || command.areaManager.getArea(m.pos).getDanger() < command.areaManager.getArea(best.getPos()).getDanger()) {
                best = m.getEnemy();
            }
        }
        for (Enemy e : command.getEnemyUnits(false)) {
            if (e.getDPS() > 0) {
                continue;
            }
            if (best == null || command.areaManager.getArea(e.getPos()).getDanger() < command.areaManager.getArea(best.getPos()).getDanger()) {
                best = e;
            }
        }
        target = null;
        targetArea = null;
        if (best != null && reachableAreas.contains(command.areaManager.getArea(best.getPos()))) {
            target = best;
            troopIdle(t);
            return;
        }
        Area maxDanger = null;
        for (Area a : reachableAreas) {
            if (a.equals(command.areaManager.getArea(t.getPos()))) {
                continue;
            }
            if (maxDanger == null || a.getDanger() > maxDanger.getDanger()) {
                maxDanger = a;
            }
        }
        if (maxDanger != null && maxDanger.getDanger() > 0) {
            targetArea = maxDanger;
            troopIdle(t);
        }
        Enemy mostValuableEnemy = null;
        for (Enemy e : command.getEnemyUnits(true)) {
            if (mostValuableEnemy == null || mostValuableEnemy.getMetalCost() < e.getMetalCost()) {
                mostValuableEnemy = e;
            }
        }
        if (mostValuableEnemy != null) {

            for (Area a : reachableAreas) {
                if (a.equals(command.areaManager.getArea(t.getPos()))) {
                    continue;
                }
                if (maxDanger == null || a.distanceTo(mostValuableEnemy.getPos()) < maxDanger.distanceTo(mostValuableEnemy.getPos())) {
                    maxDanger = a;
                }
            }
            if (maxDanger != null) {
                targetArea = maxDanger;
                troopIdle(t);
            }
        }

        for (Area a : reachableAreas) {
            if (!a.isInLOS()) {
                t.assignTask(new MoveTask(a.getPos(), command.getCurrentFrame() + 150, this, command.pathfinder.AVOID_ENEMIES, command));
                return;
            }
        }
    }

    @Override
    public void abortedTask(Task t) {
    }

    @Override
    public void finishedTask(Task t) {
    }

    @Override
    public SquadManager getInstance(Collection<UnitDef> availableUnits) {
        lastScoutSquad = command.getCurrentFrame();
        return new ScoutSquad(fighterHandler, command, clbk, availableUnits);
    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {
        super.unitDestroyed(e, killer);
        if (target != null && target.equals(e)) {
            target = null;
        }
    }

    @Override
    public void enemyEnterLOS(Enemy e) {
        if (aisquad != null && e.distanceTo(aisquad.getPos()) < 1000) {
            aisquad.getArea().updateEnemies();
            troopIdle(aisquad);
        }
    }
    
    
    @Override
    public boolean retreatForRepairs(AITroop u) {
        return true;
    }
}
