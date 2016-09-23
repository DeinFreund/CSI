/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers;

import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import com.springrts.ai.oo.clb.UnitDef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import zkcbai.Command;
import zkcbai.UpdateListener;
import zkcbai.helpers.EconomyManager;
import zkcbai.helpers.EconomyManager.Budget;
import zkcbai.helpers.Pathfinder;
import zkcbai.helpers.Pathfinder.MovementType;
import zkcbai.helpers.ZoneManager.Area;
import zkcbai.unitHandlers.betterSquads.*;
import zkcbai.unitHandlers.units.AISquad;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.BuildTask;
import zkcbai.unitHandlers.units.tasks.Task;
import zkcbai.unitHandlers.units.tasks.TaskIssuer;
import zkcbai.unitHandlers.units.tasks.WaitTask;

/**
 *
 * @author User
 */
public class FactoryHandler extends UnitHandler implements UpdateListener {

    private Set<UnitDef> builtFacs = new HashSet();
    private int constructorRequests = 0;
    private Collection<Factory> factories = new HashSet();
    private Map<AIUnit, Factory> facmap = new HashMap();
    private List<SquadManager> squads = new ArrayList();
    private Queue<SquadManager> startsquads = new LinkedList();

    public FactoryHandler(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        squads.add(new BuilderSquad(cmd.getFighterHandler(), cmd, cmd.getCallback()));
        squads.add(new CCRSquad(cmd.getFighterHandler(), cmd, cmd.getCallback()));
        squads.add(new ScoutSquad(cmd.getFighterHandler(), cmd, cmd.getCallback()));
        squads.add(new RaiderSquad(cmd.getFighterHandler(), cmd, cmd.getCallback()));
        squads.add(new AssaultSquad(cmd.getFighterHandler(), cmd, cmd.getCallback()));
        squads.add(new AvengerSquad(cmd.getFighterHandler(), cmd, cmd.getCallback()));
        squads.add(new BansheeSquad(cmd.getFighterHandler(), cmd, cmd.getCallback()));
        //startsquads.add(squads.get(0));
        //startsquads.add(squads.get(0));

        //cmd.addSingleUpdateListener(this, assaultFrame);
    }

    @Override
    public AIUnit addUnit(Unit u) {
        AIUnit au = new AIUnit(u, this);
        aiunits.put(u.getUnitId(), au);
        builtFacs.add(u.getDef());
        Factory fac = new Factory(au);
        factories.add(fac);
        facmap.put(au, fac);

        recalculateReachable(); //this call is only for gui
        return au;
    }

    BuildTask lastCaretakerRequest;

    @Override
    public void troopIdle(AIUnit u) {
        if (facmap.containsKey(u)) {
            if (!factoryIdle(facmap.get(u))) {
                u.wait(command.getCurrentFrame() + 15);
            }else{
                facmap.get(u).buildNextUnit();
            }
        } else {
            throw new AssertionError("Unknown unit in FacHandler");
        }
    }

    /**
     *
     * @param fac
     * @return whether the fac is no longer idle
     */
    public boolean factoryIdle(Factory fac) {
        //if (!u.getUnit().getCurrentCommands().isEmpty()) throw new AssertionError("Unit not really idle");
        if (constructorRequests > 0 && false) {

            for (UnitDef ud : fac.getBuildOptions()) {
                if (!ud.getBuildOptions().isEmpty()) {
                    fac.queueUnit(ud);
                    constructorRequests--;
                    return true;
                }
            }
        }

        if (command.economyManager.getRemainingBudget(EconomyManager.Budget.offense) > 0/* || clbk.getEconomy().getCurrent(command.metal) > 250*/) {

            float unitDensity = 1000000 * command.getFighterHandler().getFighters().size()
                    / command.getCallback().getMap().getWidth() * command.getCallback().getMap().getHeight();

            final float targetDensity = 1;
            float efficiencyMult = 1f / (-Math.min(0, (float) Math.log(unitDensity / targetDensity + 1e-6)) + 1);
            //command.debug("Unit density is " + unitDensity + " / " + targetDensity + " -> mult: " + efficiencyMult);

            /*if (command.economyManager.getRemainingOffenseBudget() > 500 && (lastCaretakerRequest == null || lastCaretakerRequest.getResult() != null)
                    && command.getCurrentFrame() > 30*60*5) {
                lastCaretakerRequest = command.getBuilderHandler().requestCaretaker(fac.unit.getPos());
            }*/
 /*Enemy worst = null;
            float mi = Float.MAX_VALUE;
            for (Enemy e : command.getEnemyUnits(false)) {
                float counter = 0;
                for (AIUnit au : command.getFighterHandler().getFighters()) {
                    counter += au.getEfficiencyAgainst(e) * au.getUnit().getDef().getCost(command.metal);
                }
                counter /= e.getDef().getCost(command.metal);
                counter *= Math.random() / 2 + 0.75; // +- 25% randomness
                if (counter < mi) {
                    mi = counter;
                    worst = e;
                }
            }
            if (worst != null) {
                command.debug("Worst enemy is " + worst.getDef().getHumanName());
            } else {
                command.debug("No enemies found yet");
            }
            UnitDef best = null;
            double bestEff = 0;
            for (UnitDef ud : u.getUnit().getDef().getBuildOptions()) {
                //command.debug(ud.getTooltip());
                if (ud.isAbleToRepair()) {
                    continue;
                }
                double effectiveEff = 0;
                if (worst != null) {
                    effectiveEff = command.killCounter.getEfficiency(ud, worst.getDef())
                            * Math.pow(efficiencyMult, 1f / ud.getCost(command.metal));
                    command.debug("Efficiency mult is " + Math.pow(efficiencyMult, 1f / ud.getCost(command.metal))
                            + " for cost of " + ud.getCost(command.metal));
                }
                if (best == null
                        || (worst != null && effectiveEff > bestEff)
                        || (worst == null && (ud.getCost(command.metal) < best.getCost(command.metal) && ud.getTooltip().contains("aider")))) {

                    best = ud;
                    bestEff = effectiveEff;
                }
            }
            if (best == null) {
                for (UnitDef ud : u.getUnit().getDef().getBuildOptions()) {
                    //command.debug(ud.getTooltip());
                    if (ud.isAbleToRepair()) {
                        continue;
                    }
                    if (best == null
                            || (worst != null && command.killCounter.getEfficiency(ud, worst.getDef()) > command.killCounter.getEfficiency(best, worst.getDef()))
                            || (worst == null && (ud.getCost(command.metal) < best.getCost(command.metal)))) {

                        best = ud;
                    }
                }
            }
             */
            if (fac.getBuildQueue().isEmpty()) {
                Collection<UnitDef> buildOptions = getBuildOptions();
                SquadManager best = null;
                for (SquadManager sq : squads) {
                    if ((best == null || sq.getUsefulness() > best.getUsefulness()) && sq.getRequiredUnits(fac.getBuildOptions()) != null
                            && !sq.getRequiredUnits(fac.getBuildOptions()).isEmpty()) {
                        best = sq;
                    }
                }
                boolean isStartSquad = false;
                if (!startsquads.isEmpty()) {
                    do {
                        best = startsquads.poll();
                        isStartSquad = true;
                    } while (best.getRequiredUnits(fac.getBuildOptions()) == null && !startsquads.isEmpty());
                }
                if (best != null && (best.getUsefulness() > 0.001f || isStartSquad)) {
                    command.debug("Best squad is " + best.getClass().getName());
                    fac.queueUnits(best.getRequiredUnits(fac.getBuildOptions()));
                    float cost = 0;
                    for (UnitDef ud : best.getRequiredUnits(fac.getBuildOptions())) {
                        cost += ud.getCost(command.metal);
                    }
                    best.getInstance(fac.getBuildOptions());
                    return true;
                }
            }
        }
        if (fac.getBuildQueue().isEmpty()) {
            //command.debug("Nothing to build in " + fac.unit.getDef().getHumanName());
        }
        return !fac.getBuildQueue().isEmpty();
    }

    protected void recalculateReachable() {
        for (Area a : command.areaManager.getAreas()) {
            a.setReachable(false);
        }
        for (AIUnit au : aiunits.values()) {
            MovementType mt = Pathfinder.MovementType.getMovementType(au.getDef().getBuildOptions().get(0));
            for (Area a : au.getArea().getConnectedAreas(mt)) {
                a.setReachable();
            }
        }
    }

    public void requestConstructor() {
        if (!startsquads.isEmpty()) {
            return;
        }
        constructorRequests = 1;
    }

    @Override
    public void abortedTask(Task t) {
    }

    @Override
    public void finishedTask(Task t) {
    }

    public Collection<UnitDef> getBuildOptions() {
        Collection<UnitDef> retval = new HashSet();
        for (Factory f : getFacs()) {
            retval.addAll(f.getBuildOptions());
        }
        return retval;
    }

    protected float getMovementTypeMultiplier(MovementType mt) {
        switch (mt) {
            case air:
                return 1;
            case spider:
                return 2;
            case bot:
                return 3;
            case vehicle:
                return 4;
            default:
                throw new UnsupportedOperationException("Unimplemented MovementType");
        }
    }

    private int facsPlanned = 0;

    /**
     *
     * @param buildPositions container in which the possible building areas are
     * returned<br />
     * will be cleared
     * @return the UnitDef of the best factory
     */
    public UnitDef getNextFac(Set<Area> buildPositions) {
        recalculateReachable();
        UnitDef bestFac = null;
        Set<Area> position = null;
        float bestScore = -1;

        for (UnitDef ud : command.getCallback().getUnitDefByName("armrectr").getBuildOptions()) {
            boolean allMovable = ud.getBuildOptions().size() > 0;
            for (UnitDef buildable : ud.getBuildOptions()) {
                if (buildable.getSpeed() < 0.1) {
                    allMovable = false;
                }
            }
            if (allMovable) {//is factory
                //command.debug(ud.getHumanName() + " is a fac");
                if (ud.getBuildOptions().get(0).getCost(command.metal) > 900) {
                    continue; //no strider plop pls
                }
                MovementType mt = MovementType.getMovementType(ud.getBuildOptions().get(0));
                Set<Area> bestset = new HashSet();
                int bestsize = 0;
                Set<Area> totset = new HashSet();
                for (Area a : command.areaManager.getAreas()) {
                    if (totset.contains(a)) {
                        continue;
                    }
                    Set<Area> set = a.getConnectedAreas(mt);
                    int size = set.size();
                    for (Area area : set) {
                        if (area.isReachable()) {
                            size--;
                        }
                    }
                    if (size > bestsize || (size >= bestsize && set.size() > bestset.size())) {
                        bestset = set;
                        bestsize = size;
                    }
                    totset.addAll(set);
                }
                float score = ((float) Math.random() / 20f + 1f) * bestsize * getMovementTypeMultiplier(mt);
                if (score > bestScore) {
                    bestScore = score;
                    bestFac = ud;
                    position = bestset;
                }
            }
        }
        if (bestFac == null) {
            throw new AssertionError("Didn't find any valid fac to build");
        }
        if (position.size() < 10) {
            command.debug("Warning, only " + position.size() + " fac build areas");
        }
        buildPositions.clear();
        buildPositions.addAll(position);
        if (facsPlanned == 1) {
            bestFac = clbk.getUnitDefByName("factorygunship"); //hardcode light vehicle factory 
            buildPositions.clear();
            buildPositions.addAll(command.areaManager.getAreas());
        }
        if (facsPlanned == 2) {
            bestFac = clbk.getUnitDefByName("factoryshield"); //hardcode light vehicle factory 
        }
        if (facsPlanned == 3) {
            bestFac = clbk.getUnitDefByName("factoryplane"); //hardcode light vehicle factory 
            buildPositions.clear();
            buildPositions.addAll(command.areaManager.getAreas());
        }
        facsPlanned++;
        return bestFac;
    }

    public Collection<Factory> getFacs() {
        return factories;
    }

    @Override
    public void removeUnit(AIUnit u) {
        if (!aiunits.containsKey(u.getUnit().getUnitId())) {
            return;
        }
        aiunits.remove(u.getUnit().getUnitId());
        builtFacs.clear();
        for (AIUnit au : aiunits.values()) {
            builtFacs.add(au.getUnit().getDef());
        }
        factories.remove(facmap.get(u));
        facmap.remove(u);
    }
    
    public List<UnitDef> getQueue(){
        List<UnitDef> queue = new ArrayList();
        for (Factory f : factories){
            queue.addAll(f.getBuildQueue());
        }
        return queue;
    }
    
    public int getQueuedCount(UnitDef ud){
        int res = 0;
        for (Factory f : factories){
            for (UnitDef u : f.getBuildQueue()){
                if (u.equals(ud)) res ++;
            }
        }
        return res;
    }

    @Override
    public void unitDestroyed(AIUnit u, Enemy e) {
        removeUnit(u);
    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {
    }

    @Override
    public void reportSpam() {
        throw new RuntimeException("I spammed MoveTasks!");
    }

    @Override
    public void troopIdle(AISquad s) {
    }

    @Override
    public void update(int frame) {
    }

    @Override
    public boolean retreatForRepairs(AITroop u) {
        return false;
    }

    public class Factory implements TaskIssuer {

        public final AIUnit unit;

        protected Queue<UnitDef> buildOrders;
        protected BuildTask currentTask;

        public Factory(AIUnit unit) {
            this.unit = unit;
            buildOrders = new LinkedList<>();
        }

        public BuildTask getCurrentTask() {
            return currentTask;
        }

        public Queue<UnitDef> getBuildQueue() {
            return buildOrders;
        }

        /**
         *
         * @return time for which the factory will be busy
         */
        public float getBuildTime() {
            float sum = 0;
            for (UnitDef ud : buildOrders) {
                sum += ud.getBuildTime();
                command.debug("build time of " + ud.getHumanName() + " is " + ud.getBuildTime() + " secs.");
            }
            return sum;
        }

        public void queueUnits(Collection<UnitDef> ud) {
            for (UnitDef u : ud) {
                queueUnit(u);
            }
        }

        public void queueUnit(UnitDef ud) {
            command.debug("queued " + ud.getHumanName());
            if (!unit.getDef().getBuildOptions().contains(ud)) {
                throw new AssertionError("can't build " + ud.getHumanName() + " in " + unit.getDef().getHumanName());
            }
            buildOrders.add(ud);
            buildNextUnit();
        }

        public void buildUnit(UnitDef unit) {
            buildUnit(unit, false);
        }

        public void buildUnit(UnitDef unit, boolean force) {
            if (currentTask != null) {
                if (force) {
                    currentTask.cancel();
                    command.getBuilderHandler().unregisterBuildTask(currentTask);
                    currentTask = null;
                } else {
                    return;
                }
            }
            if (command.economyManager.getRemainingBudget(Budget.offense) < 0) {
                this.unit.assignTask(new WaitTask(command.getCurrentFrame() + 30, this));
            } else {
                currentTask = new BuildTask(unit, this.unit.getPos(), 0, Budget.offense, this, clbk, command);
                command.getBuilderHandler().registerBuildTask(currentTask);
                this.unit.assignTask(currentTask);
            }
        }

        public void buildNextUnit() {
            if (!buildOrders.isEmpty()) {
                buildUnit(buildOrders.peek());
            } else {
                troopIdle(unit);
            }
        }

        public List<UnitDef> getBuildOptions() {
            return unit.getDef().getBuildOptions();
        }

        @Override
        public void abortedTask(Task t) {
            
            command.getBuilderHandler().unregisterBuildTask(currentTask);
            currentTask = null;
            buildNextUnit();
        }

        @Override
        public void finishedTask(Task t) {
            if (t.equals(currentTask)) {
                buildOrders.poll();
            }
            command.getBuilderHandler().unregisterBuildTask(currentTask);
            currentTask = null;
            buildNextUnit();
        }

        @Override
        public void reportSpam() {
            throw new RuntimeException("this is so deprecated");
        }

    }

}
