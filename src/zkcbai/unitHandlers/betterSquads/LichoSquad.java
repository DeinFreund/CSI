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
import zkcbai.helpers.EconomyManager;
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
import zkcbai.unitHandlers.units.tasks.FightTask;
import zkcbai.unitHandlers.units.tasks.MoveTask;
import zkcbai.unitHandlers.units.tasks.Task;

/**
 *
 * @author User
 */
public class LichoSquad extends SquadManager {

    public LichoSquad(FighterHandler fighterHandler, Command command, OOAICallback callback) {
        this(fighterHandler, command, callback, null);
        for (String s : bomberIds) {
            bombers.add(command.getCallback().getUnitDefByName(s));
        }
    }

    public LichoSquad(FighterHandler fighterHandler, Command command, OOAICallback callback, Collection<UnitDef> availableUnits) {
        super(fighterHandler, command, callback, availableUnits);

    }

    final static private String[] bomberIds = {"armcybr"};
    public final static Set<UnitDef> bombers = new HashSet();

    @Override
    public List<UnitDef> getRequiredUnits(Collection<UnitDef> availableUnits) {
        Set<UnitDef> unitset = new HashSet(availableUnits);
        for (UnitDef ud : bombers) {
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
        int fighters = 0;
        int aa = 0;
        int bomber = 0;
        for (AIUnit au : command.getUnits()) {
            if (bombers.contains(au.getDef())) {
                bomber++;
            }
            if (AvengerSquad.fighters.contains(au.getDef())) {
                fighters++;
            }
            if (AntiAirSquad.antiair.contains(au.getDef())) {
                aa++;
            }
        }
        float groundvalue, fightervalue;
        groundvalue = fightervalue = 0;
        for (AIUnit au : command.getUnits()){
            if (au.getDef().isAbleToAttack() && au.getDef().getBuildSpeed() < 0.1 && au.getDef().getSpeed() > 0.1){
                groundvalue += au.getMetalCost();
            }
            if (AvengerSquad.fighters.contains(au.getDef()) || BansheeSquad.fighters.contains(au.getDef()) || bombers.contains(au.getDef())){
                fightervalue += au.getMetalCost();
            }
        }
        if (groundvalue + 300 < 2 * fightervalue){
            return 0f;
        }

        if (command.getCurrentFrame() - 30 * 60 * 10 > bomber * 30 * 60 * 8) {
            return 0.91f;
        }
        if (command.economyManager.getRemainingBudget(EconomyManager.Budget.offense) > 1800) {
            return 0.8f;
        }
        command.debug("LichoSquad usefulness: " + Math.min(0.9f, Math.max((fighters + aa - 5 * bomber) / 20f, 0.5f) * 2f - 1.01f));
        return Math.min(0.9f, Math.max((fighters + aa - 5 * bomber) / 20f, 0.5f) * 2f - 1.01f);
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
        return new LichoSquad(fighterHandler, command, clbk, availableUnits);
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
