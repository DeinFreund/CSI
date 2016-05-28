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
import zkcbai.helpers.ZoneManager.Zone;
import zkcbai.unitHandlers.FighterHandler;
import zkcbai.unitHandlers.units.AISquad;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.FightTask;
import zkcbai.unitHandlers.units.tasks.Task;

/**
 *
 * @author User
 */
public class CCRSquad extends SquadManager {

    public CCRSquad(FighterHandler fighterHandler, Command command, OOAICallback callback) {
        this(fighterHandler, command, callback, null);
    }

    public CCRSquad(FighterHandler fighterHandler, Command command, OOAICallback callback, Collection<UnitDef> availableUnits) {
        super(fighterHandler, command, callback, availableUnits);

    }


    private AISquad aisquad;

    @Override
    public List<UnitDef> getRequiredUnits(Collection<UnitDef> availableUnits) {
        
        List<UnitDef> required = new ArrayList<>();
        required.add(clbk.getUnitDefByName("cormist"));
        required.add(clbk.getUnitDefByName("cormist"));
        required.add(clbk.getUnitDefByName("cormist"));
        required.add(clbk.getUnitDefByName("cormist"));
        required.add(clbk.getUnitDefByName("corned"));
        
        return required;
    }

    @Override
    public float getUsefulness() {
        if (command.getCurrentFrame() < 30 * 60 * 1) {
            return 0f;
        }
        return 1f;
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

    private final AreaChecker nearestOwnedChecker = new AreaChecker() {

        @Override
        public boolean checkArea(Area a) {
            return (a.getZone() == Zone.own);
        }

    };

    @Override
    public void troopIdle(AITroop t) {
        /*if (!getRequiredUnits(availableUnits).isEmpty() && !finished) {
            t.wait(command.getCurrentFrame() + 60);
            return;
        } else {
            finished = true;
        }*/
        if (t instanceof AIUnit) {
            throw new AssertionError("all units should be in aisquad instead of single aiunits");
        } else {
            //command.debug("raidersquad has " + ((AISquad)t).getUnits().size() + " units");
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
        return new CCRSquad(fighterHandler, command, clbk, availableUnits);
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
