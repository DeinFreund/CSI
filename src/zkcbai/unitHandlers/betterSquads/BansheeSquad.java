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
import static zkcbai.unitHandlers.betterSquads.SupportSquad.supports;
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
public class BansheeSquad extends SquadManager {

    public BansheeSquad(FighterHandler fighterHandler, Command command, OOAICallback callback) {
        this(fighterHandler, command, callback, null);
        for (String s : fighterIds) {
            fighters.add(command.getCallback().getUnitDefByName(s));
        }
    }

    public BansheeSquad(FighterHandler fighterHandler, Command command, OOAICallback callback, Collection<UnitDef> availableUnits) {
        super(fighterHandler, command, callback, availableUnits);

    }

    final static private String[] fighterIds = {"armkam"};
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
        float groundValue = 0;
        float airValue = 0;
        for (AIUnit au : command.getUnits()) {
            if (au.isBuilding() || au.getDPS() < 1) {
                continue;
            }
            if (au.getDef().isAbleToFly()) {
                airValue += au.getMetalCost();
            } else {
                groundValue += au.getMetalCost();
            }
        }
        if (groundValue < 2 * airValue) return 0.0f;
        if (command.getBansheeHandler().getUnits().size() < 3) {
            return 0.95f;
        }
        if (command.getCurrentFrame() < 30 * 60 * 5) {
            return 0.9f;
        }
        if (command.getBansheeHandler().getUnits().size() < 5) {
            return 0.5f;
        }
        return 0f;
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
        return new BansheeSquad(fighterHandler, command, clbk, availableUnits);
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
