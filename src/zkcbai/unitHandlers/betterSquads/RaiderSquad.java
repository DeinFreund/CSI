/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.betterSquads;

import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.UnitDef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import zkcbai.Command;
import zkcbai.helpers.AreaChecker;
import zkcbai.helpers.ZoneManager.Area;
import zkcbai.helpers.ZoneManager.Mex;
import zkcbai.helpers.ZoneManager.Zone;
import zkcbai.unitHandlers.FighterHandler;
import static zkcbai.unitHandlers.betterSquads.ScoutSquad.dangerChecker;
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
public class RaiderSquad extends SquadManager {

    public RaiderSquad(FighterHandler fighterHandler, Command command, OOAICallback callback) {
        this(fighterHandler, command, callback, null);
        for (String s : raiderIds) {
            raiders.add(command.getCallback().getUnitDefByName(s));
        }
        for (String s : porcIds) {
            porc.add(command.getCallback().getUnitDefByName(s));
        }
    }

    public RaiderSquad(FighterHandler fighterHandler, Command command, OOAICallback callback, Collection<UnitDef> availableUnits) {
        super(fighterHandler, command, callback, availableUnits);
        
    }

    final static private String[] raiderIds = {"armpw", "corak", "corgator", "logkoda", "armkam", "amphraider3", "subraider", "corsh"};
    final static List<UnitDef> raiders = new ArrayList();

    final static private String[] porcIds = {"corrl", "armllt"};
    final static private List<UnitDef> porc = new ArrayList();

    private AISquad aisquad;

    @Override
    public List<UnitDef> getRequiredUnits(Collection<UnitDef> availableUnits) {
        float reqmet = 300;
        for (AIUnit au : units) {
            if (raiders.contains(au.getDef())) {
                reqmet -= au.getMetalCost();
            }
        }
        if (this.aisquad != null && units.size() != aisquad.getUnits().size()) {
            throw new AssertionError("didnt add all units correctly to raidersquad " + aisquad.getUnits().size() + " / " + units.size());
        }
        Set<UnitDef> unitset = new HashSet(availableUnits);
        for (UnitDef ud : raiders) {
            if (unitset.contains(ud)) {
                List<UnitDef> req = new ArrayList();
                while (reqmet > 0) {
                    req.add(ud);
                    reqmet -= ud.getCost(command.metal);
                }
                return req;
            }
        }
        command.debug("Warning(RaiderSquad): Couldn't find any required units in set of available Units: ");
        for (UnitDef ud : availableUnits) {
            command.debug(ud.getHumanName());
        }
        return null;
    }

    @Override
    public float getUsefulness() {
        if (command.getCurrentFrame() < 30*60*5) {
            return 0.7f;
        }
        int vis = 0;
        for (Area a : command.areaManager.getAreas()) {
            if (a.isInLOS()) {
                vis++;
            }
        }
        return 0.9f - (Math.min(0.5f, Math.max(0.1f, (float) vis / command.areaManager.getAreas().size())) - 0.1f) / 0.4f;
    }

    @Override
    public AIUnit.UnitType getType() {
        return AIUnit.UnitType.raider;
    }

    @Override
    public void troopIdle(AIUnit u) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void troopIdle(AISquad s) {
        throw new UnsupportedOperationException();
    }

    Enemy target = null;

    @Override
    public void addUnit(AIUnit u) {
        super.addUnit(u);
        if (aisquad == null) {
            aisquad = new AISquad(this);
        }
        aisquad.addUnit(u);
        command.debug("added " + u.getDef().getHumanName() + " to raidersquad, size is now " + aisquad.getUnits().size());
    }

    @Override
    public Set<AIUnit> disband() {
        for (AIUnit au : aisquad.getUnits()) {
            aisquad.removeUnit(au, this);
        }
        return super.disband();
    }

    

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
            //command.debug("raidersquad has " + ((AISquad)t).getUnits().size() + " units");
        }

        final Set<Area> reachableAreas = command.areaManager.getArea(t.getPos()).getConnectedAreas(t.getMovementType(), dangerChecker);
        AreaChecker reachableChecker = new AreaChecker() {

            @Override
            public boolean checkArea(Area a) {
                return reachableAreas.contains(a);
            }
        };
        Collection<Enemy> enemies = command.getEnemyUnitsIn(t.getPos(), 2200);
        if (enemies.size()  < command.getCallback().getFriendlyUnitsIn(t.getPos(), 2200).size()) {
            Enemy best = null;

            for (Enemy e : enemies) {
                if (best == null || e.getDPS() / e.getHealth() > best.getDPS() / best.getHealth()) {
                    best = e;
                }
            }
            if (best != null) {
                t.assignTask(new AttackTask(best, command.getCurrentFrame() + 60, this, command));
                return;
            }
        }
        if (target != null && Math.random() < 0.6) {
            Enemy best = this.target;
            for (Enemy e : command.areaManager.getArea(target.getPos()).getNearbyEnemies()) {
                if (e.getMaxRange() > e.distanceTo(target.getPos())) {
                    if (best == null || e.getDPS() / e.getHealth() > best.getDPS() / best.getHealth()) {
                        best = e;
                    }
                }
            }
            if (t.distanceTo(best.getPos()) > Math.max(500, t.getMaxRange())) {
                t.assignTask(new MoveTask(best.getPos(), command.getCurrentFrame() + 150, this, command.pathfinder.AVOID_ENEMIES, command));
            } else {
                t.assignTask(new AttackTask(best, command.getCurrentFrame() + 200, this, command));
            }
            return;
        }
        Enemy best = null;
        for (Mex m : command.areaManager.getMexes()) {
            if (command.areaManager.getArea(m.pos).getNearestArea(reachableChecker).distanceTo(m.pos) > 1500) {
                continue;
            }
            if (best == null || m.distanceTo(t.getPos()) < best.distanceTo(t.getPos()) /*command.areaManager.getArea(m.pos).getDanger() < command.areaManager.getArea(best.getPos()).getDanger()*/) {
                best = m.getEnemy();
            }
        }
        for (Enemy e : command.getEnemyUnits(false)) {
            if (e.getDPS() > 40) {
                continue;
            }
            if (command.areaManager.getArea(e.getPos()).getNearestArea(reachableChecker).distanceTo(e.getPos()) > 1500) {
                continue;
            }
            if (best == null || e.distanceTo(t.getPos()) < best.distanceTo(t.getPos()) /*command.areaManager.getArea(e.getPos()).getDanger() < command.areaManager.getArea(best.getPos()).getDanger()*/) {
                best = e;
            }
        }
        if (best != null) {
            target = best;
            troopIdle(t);
            return;
        }
        for (Area a : command.areaManager.getAreas()) {
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
        return new RaiderSquad(fighterHandler, command, clbk, availableUnits);
    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {
        super.unitDestroyed(e, killer);
        if (target != null && target.equals(e)) {
            target = null;
        }
    }
    
    @Override
    public boolean retreatForRepairs(AITroop u) {
        return command.areaManager.getArea(u.getPos()).getZone() != Zone.hostile;
    }
}
