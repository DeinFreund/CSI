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
import java.util.Random;
import java.util.Set;
import zkcbai.Command;
import zkcbai.helpers.AreaChecker;
import zkcbai.helpers.ZoneManager.Area;
import zkcbai.helpers.ZoneManager.Mex;
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
public class AssaultSquad extends SquadManager {

    public AssaultSquad(FighterHandler fighterHandler, Command command, OOAICallback callback) {
        this(fighterHandler, command, callback, null);
    }

    public AssaultSquad(FighterHandler fighterHandler, Command command, OOAICallback callback, Collection<UnitDef> availableUnits) {
        super(fighterHandler, command, callback, availableUnits);
        for (String s : assaultIds) {
            assaults.add(command.getCallback().getUnitDefByName(s));
        }
        for (String s : riotIds) {
            riots.add(command.getCallback().getUnitDefByName(s));
        }
        for (String s : porcIds) {
            porc.add(command.getCallback().getUnitDefByName(s));
        }
    }

    final private String[] assaultIds = {"armzeus", "corthud", "corraid", "correap", "amphfloater", "shipraider", "nsaclash","spiderassault","slowmort", "armsnipe", "amphassault",   "corgol"};
    final static Set<UnitDef> assaults = new HashSet();
    final private String[] riotIds = {"armwar", "cormak", "arm_venom", "spiderriot", "amphriot", "corlevlr", "tawf114", "shipraider", "hoverassault", "jumpblackhole"};
    final static Set<UnitDef> riots = new HashSet();

    final private String[] porcIds = {"corrl", "armllt", "armdeva" , "corhlt", "armpb"};
    final private Set<UnitDef> porc = new HashSet();
    
    private float minWeight = 0;
    private float maxWeight = 300;

    private AISquad aisquad;
    private Random rnd = new Random();

    @Override
    public List<UnitDef> getRequiredUnits(Collection<UnitDef> availableUnits) {
        List<UnitDef> possAssault = new ArrayList();
        List<UnitDef> possRiot = new ArrayList();
        for (UnitDef ud : availableUnits){
            if (riots.contains(ud) && ud.getCost(command.metal) > minWeight && ud.getCost(command.metal) < maxWeight) possRiot.add(ud);
            if (assaults.contains(ud)&& ud.getCost(command.metal) > minWeight && ud.getCost(command.metal) < maxWeight) possAssault.add(ud);
        }
        List<UnitDef> retval = new ArrayList();
        if (!possAssault.isEmpty()){
            retval.add(possAssault.get(rnd.nextInt(possAssault.size())));
        }
        if (!possRiot.isEmpty() && rnd.nextInt(2) == 0){
            retval.add(possRiot.get(rnd.nextInt(possRiot.size())));
        }
        return retval;
    }

    @Override
    public float getUsefulness() {
        if (command.getCurrentFrame() < 30*60*0.5) return 0.01f;
        float raiders = 0;
        float all = 0;
        for (Enemy e : command.getEnemyUnits(true)){
            if (e.getDPS() < 0.1) continue;
            if (RaiderSquad.raiders.contains(e.getDef())){
                raiders += e.getMetalCost();
            }
            all += e.getMetalCost();
        }
        int assaults = 0;
        for (AIUnit au : command.getCreepHandler().getUnits()){
            if (RaiderSquad.raiders.contains(au.getDef()) || AssaultSquad.assaults.contains(au.getDef()) || riots.contains(au.getDef())) assaults ++;
        }
        minWeight = 50f *(float) Math.pow( (assaults),  0.7)*0;
        //maxWeight = 50f *(float) Math.pow( (assaults),  1.1) + 250;
        maxWeight =  (command.getCurrentFrame() / (30 * 60) + 1) * 7 * command.getBuilderHandler().getMetalIncome();
        command.debug("Assault weight span: " + minWeight + " - " + maxWeight);
        /*
        if (command.getCreepHandler().getUnits().size() > 100) {
            command.debug("Too many units for assault");
            return -1f;
        }*/
        float mini = 0.1f;
        if (command.areaManager.getMapWidth() * command.areaManager.getMapHeight() < 5000 * 5000 * command.getCommanderHandlers().size()){
            mini = 0.4f;
        }
        if (command.areaManager.getMapWidth() * command.areaManager.getMapHeight() < 4000 * 4000 * command.getCommanderHandlers().size()){
            mini = 0.6f;
        }
        if (command.areaManager.getMapWidth() * command.areaManager.getMapHeight() < 3000 * 3000 * command.getCommanderHandlers().size()){
            mini = 0.8f;
        }
        command.debug("Assault usefulness: " + Math.max(mini, Math.min(0.9f, 1f - 0.88f * raiders / all)));
        return Math.max(mini, Math.min(0.9f, 1f - 0.88f * raiders / all));
    }

    @Override
    public AIUnit.UnitType getType() {
        return AIUnit.UnitType.assault;
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
        command.debug("added " + u.getDef().getHumanName() + " to assaultsquad, size is now " + aisquad.getUnits().size());
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
        if (!getRequiredUnits(availableUnits).isEmpty() && !finishedBuilding) {
            t.wait(command.getCurrentFrame() + 60);
            return;
        } else {
            finishedBuilding = true;
        }
        if (t instanceof AIUnit) {
            throw new AssertionError("all units should be in aisquad instead of single aiunits");
        } else {
            //command.debug("assaultsquad has " + ((AISquad)t).getUnits().size() + " units");
        }

        final Set<Area> reachableAreas = t.getArea().getConnectedAreas(t.getMovementType(), dangerChecker);
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
        return new AssaultSquad(fighterHandler, command, clbk, availableUnits);
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
        return true;
    }
}
