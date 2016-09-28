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
        if (command.getCurrentFrame() < 30 * 60 * 5) {
            return 0.1f;
        }
        int fighters = 0;
        int aa = 0;
        int bomber = 0;
        for (AIUnit au : command.getUnits()){
            if (bombers.contains(au.getDef())){
                bomber ++;
            }
            if (AvengerSquad.fighters.contains(au.getDef())){
                fighters ++;
            }
            if (AntiAirSquad.antiair.contains(au.getDef())){
                aa ++;
            }
        }
        
        if (command.getCurrentFrame() - 30 * 60 * 12 > bomber * 30 * 60 * 8) {
            return 0.91f;
        }
        return Math.min(0.9f, (fighters + aa - 5 * bomber) / 20f);
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
