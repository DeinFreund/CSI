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
import zkcbai.unitHandlers.units.tasks.Task;

/**
 *
 * @author User
 */
public class BuilderSquad extends SquadManager {

    public BuilderSquad(FighterHandler fighterHandler, Command command, OOAICallback callback) {
        this(fighterHandler, command, callback, null);
        for (UnitDef ud : command.getCallback().getUnitDefs()) {
            if (ud.getBuildOptions().size() > 5 && !ud.getName().equalsIgnoreCase("armca")) {
                constructors.add(ud);
            }
        }
    }

    public BuilderSquad(FighterHandler fighterHandler, Command command, OOAICallback callback, Collection<UnitDef> availableUnits) {
        super(fighterHandler, command, callback, availableUnits);

    }

    final static List<UnitDef> constructors = new ArrayList();

    private AISquad aisquad;

    @Override
    public List<UnitDef> getRequiredUnits(Collection<UnitDef> availableUnits) {
        float reqmet = 10;
        for (AIUnit au : units) {
            if (constructors.contains(au.getDef())) {
                reqmet -= au.getMetalCost();
            }
        }
        if (this.aisquad != null && units.size() != aisquad.getUnits().size()) {
            throw new AssertionError("didnt add all units correctly to raidersquad " + aisquad.getUnits().size() + " / " + units.size());
        }
        Set<UnitDef> unitset = new HashSet(availableUnits);
        for (UnitDef ud : constructors) {
            if (unitset.contains(ud)) {
                List<UnitDef> req = new ArrayList();
                while (reqmet > 0) {
                    req.add(ud);
                    reqmet -= ud.getCost(command.metal);
                }
                return req;
            }
        }
        /*command.debug("Warning(BuilderSquad): Couldn't find any required units in set of available Units: ");
        for (UnitDef ud : availableUnits) {
            command.debug(ud.getHumanName());
        }*/
        return null;
    }

    @Override
    public float getUsefulness() {
        int bp = 0;
        int buildersBuilding = 0;
        for (UnitDef ud : command.getFactoryHandler().getQueue()) {
            bp += ud.getBuildSpeed();
            if (ud.getBuildSpeed() > 0) {
                buildersBuilding++;
            }
        }
        for (AIUnit au : command.getBuilderHandler().getBuilders()) {
            bp += au.getDef().getBuildSpeed();
        }
        if (bp - 10 > 2 * command.getBuilderHandler().getMetalIncome() || buildersBuilding * 2 >= command.getFactoryHandler().getFacs().size() || bp > 200) {
            return -1f;
        } else {
            //command.debug("Total BP only " + bp + "/" + (int)(2 * command.getBuilderHandler().getMetalIncome() + 10) + " m/s");
            return 0.91f;
        }
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

        throw new UnsupportedOperationException();
    }

    @Override
    public void abortedTask(Task t) {
    }

    @Override
    public void finishedTask(Task t) {
    }

    @Override
    public SquadManager getInstance(Collection<UnitDef> availableUnits) {
        return new BuilderSquad(fighterHandler, command, clbk, availableUnits);
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
