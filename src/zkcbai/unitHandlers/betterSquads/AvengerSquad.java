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
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import zkcbai.Command;
import zkcbai.helpers.AreaChecker;
import zkcbai.helpers.ZoneManager.Area;
import zkcbai.helpers.ZoneManager.Mex;
import zkcbai.helpers.ZoneManager.Zone;
import zkcbai.unitHandlers.FighterHandler;
import static zkcbai.unitHandlers.betterSquads.LichoSquad.bombers;
import static zkcbai.unitHandlers.betterSquads.ScoutSquad.dangerChecker;
import zkcbai.unitHandlers.units.AISquad;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.AttackTask;
import zkcbai.unitHandlers.units.tasks.FightTask;
import zkcbai.unitHandlers.units.tasks.MoveTask;
import zkcbai.unitHandlers.units.tasks.Task;

/**
 *
 * @author User
 */
public class AvengerSquad extends SquadManager {

    public AvengerSquad(FighterHandler fighterHandler, Command command, OOAICallback callback) {
        this(fighterHandler, command, callback, null);
        for (String s : fighterIds) {
            fighters.add(command.getCallback().getUnitDefByName(s));
        }
    }

    public AvengerSquad(FighterHandler fighterHandler, Command command, OOAICallback callback, Collection<UnitDef> availableUnits) {
        super(fighterHandler, command, callback, availableUnits);

    }

    final static private String[] fighterIds = {"fighter"};
    public final static Set<UnitDef> fighters = new HashSet();


    @Override
    public List<UnitDef> getRequiredUnits(Collection<UnitDef> availableUnits) {
        Set<UnitDef> unitset = new HashSet(availableUnits);
        for (UnitDef ud : fighters) {
            if (unitset.contains(ud)) {
                List<UnitDef> req = new ArrayList();
                    req.add(ud);
                return req;
            }
        }
        return null;
    }

    @Override
    public float getUsefulness() {
        float aavalue, enemyairvalue, groundvalue, fightervalue;
        aavalue = enemyairvalue = groundvalue = fightervalue = 0;
        for (AIUnit au : command.getUnits()){
            if (au.getDef().isAbleToAttack() && au.getDef().getBuildSpeed() < 0.1 && au.getDef().getSpeed() > 0.1){
                groundvalue += au.getMetalCost();
            }
            if (AvengerSquad.fighters.contains(au.getDef()) || AntiAirSquad.antiair.contains(au.getDef())){
                aavalue += au.getMetalCost();
            }
            if (AvengerSquad.fighters.contains(au.getDef()) || BansheeSquad.fighters.contains(au.getDef())){
                fightervalue += au.getMetalCost();
            }
        }
        for (Enemy e : command.getAvengerHandler().getEnemyAir()){
            enemyairvalue += e.getMetalCost();
        }
        if (groundvalue + 300 < 3 * fightervalue && command.getAvengerHandler().getUnits().size() > 2){
            return 0f;
        }
        return Math.min(0.9f, enemyairvalue / (aavalue + 100f) + ((command.getAvengerHandler().getUnits().size() < 3) ? 0.3f : -0.01f));
        //return 0.9f - (Math.min(0.5f, Math.max(0.1f, (float) vis / command.areaManager.getAreas().size())) - 0.1f) / 0.4f;
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
    public void abortedTask(Task t) {
    }

    @Override
    public void finishedTask(Task t) {
    }

    @Override
    public SquadManager getInstance(Collection<UnitDef> availableUnits) {
        return new AvengerSquad(fighterHandler, command, clbk, availableUnits);
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
        return u.getArea().getZone() != Zone.hostile;
    }
}
