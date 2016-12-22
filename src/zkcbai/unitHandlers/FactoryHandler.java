/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers;

import com.springrts.ai.oo.AIFloat3;
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
import static zkcbai.helpers.Pathfinder.MovementType.air;
import zkcbai.helpers.PositionChecker;
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
    private Map<SquadManager, Float> usefulCache = new HashMap();
    private Map<SquadManager, Integer> usefulLast = new HashMap();

    public FactoryHandler(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        squads.add(new BuilderSquad(cmd.getFighterHandler(), cmd, cmd.getCallback()));
        squads.add(new RaiderSquad(cmd.getFighterHandler(), cmd, cmd.getCallback()));
        //squads.add(new CCRSquad(cmd.getFighterHandler(), cmd, cmd.getCallback()));
        squads.add(new ScoutSquad(cmd.getFighterHandler(), cmd, cmd.getCallback()));
        squads.add(new AssaultSquad(cmd.getFighterHandler(), cmd, cmd.getCallback()));
        squads.add(new AvengerSquad(cmd.getFighterHandler(), cmd, cmd.getCallback()));
        squads.add(new BansheeSquad(cmd.getFighterHandler(), cmd, cmd.getCallback()));
        squads.add(new SupportSquad(cmd.getFighterHandler(), cmd, cmd.getCallback()));
        squads.add(new AntiAirSquad(cmd.getFighterHandler(), cmd, cmd.getCallback()));
        squads.add(new LichoSquad(cmd.getFighterHandler(), cmd, cmd.getCallback()));
        squads.add(new VultureSquad(cmd.getFighterHandler(), cmd, cmd.getCallback()));
        startsquads.add(squads.get(0));

        //cmd.addSingleUpdateListener(this, assaultFrame);
        cmd.addUpdateListener(this);
    }

    @Override
    public AIUnit addUnit(Unit u) {
        AIUnit au = new AIUnit(u, this);
        aiunits.put(u.getUnitId(), au);
        builtFacs.add(u.getDef());
        Factory fac = new Factory(au);
        factories.add(fac);
        facmap.put(au, fac);

        recalculateReachable();
        return au;
    }

    BuildTask lastCaretakerRequest;

    @Override
    public void troopIdle(AIUnit u) {
        if (facmap.containsKey(u)) {
            if (!factoryIdle(facmap.get(u))) {
                u.wait(command.getCurrentFrame() + 30);
                command.debug("Nothing to do for " + u.getDef().getHumanName());
            } else {
                command.debug(u.getDef().getHumanName() + " building next unit");
                facmap.get(u).buildNextUnit();
            }
        } else {
            throw new AssertionError("Unknown unit in FacHandler");
        }
    }

    private float getCachedUsefulness(SquadManager sq) {
        long time = System.nanoTime();
        if (!usefulLast.containsKey(sq)) {
            usefulLast.put(sq, -1000);
        }
        if (command.getCurrentFrame() - usefulLast.get(sq) > 60) {
            usefulLast.put(sq, command.getCurrentFrame());
            usefulCache.put(sq, sq.getUsefulness());
        }
        time = System.nanoTime() - time;
        if (time > 0.5e6) {
            command.debug("Getting usefulness of " + sq.getClass().getName() + " took " + time + " ns.");
        }
        return usefulCache.get(sq);
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
                    if ((best == null || getCachedUsefulness(sq) > getCachedUsefulness(best)) && sq.getRequiredUnits(fac.getBuildOptions()) != null
                            && !sq.getRequiredUnits(fac.getBuildOptions()).isEmpty()) {
                        best = sq;
                    }
                }
                boolean isStartSquad = false;
                if (!startsquads.isEmpty() && startsquads.peek().getRequiredUnits(fac.getBuildOptions()) != null) {
                    do {
                        best = startsquads.poll();
                        isStartSquad = true;
                    } while (best.getRequiredUnits(fac.getBuildOptions()) == null && !startsquads.isEmpty());
                }
                if (best != null && (getCachedUsefulness(best) > 0.001f || isStartSquad)) {
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
            if (mt == MovementType.air) {
                continue;
            }
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
        finishedTask(t);
    }

    @Override
    public void finishedTask(Task t) {
        if (t instanceof BuildTask) {
            BuildTask bt = (BuildTask) t;
            command.getBuilderHandler().unregisterBuildTask(bt);
        }
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
                return 0;
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
     * @param buildPositions container in which the possible building areas are returned<br />
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
                if (ud.getBuildOptions().get(0).getCost(command.metal) > 900 || facsPlanned == 0 && (ud.getName().contains("tank"))) {
                    continue; //no strider plop pls
                }
                MovementType mt = MovementType.getMovementType(ud.getBuildOptions().get(0));
                Set<Area> bestset = new HashSet();
                float bestsize = 0;
                Set<Area> totset = new HashSet();
                for (Area a : command.areaManager.getAreas()) {
                    if (totset.contains(a)) {
                        continue;
                    }
                    Set<Area> set = a.getConnectedAreas(mt);
                    float size = set.size();
                    for (Area area : set) {
                        if (area.isReachable()) {
                            size -= 0.9f;
                        }
                    }
                    if (size > bestsize || (size >= bestsize && set.size() > bestset.size())) {
                        bestset = set;
                        bestsize = size;
                    }
                    totset.addAll(set);
                }
                float score = ((float) Math.random() / 20f + 1f) * (bestsize + 1) * getMovementTypeMultiplier(mt);
                for (Factory f : factories) {
                    if (f.unit.getDef().equals(ud)) {
                        score /= 10;
                    }
                }
                for (BuildTask bt : command.getBuildTasks()) {
                    if (bt.getBuilding().equals(ud)) {
                        score /= 10;
                    }
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestFac = ud;
                    position = bestset;
                }
            }
        }
        if (bestFac == null) {
            throw new AssertionError("Didn't find any valid fac to build");
        } else {
            command.debug("Chose " + bestFac.getHumanName() + " with score of " + bestScore);
        }
        if (position.size() < 10) {
            command.debug("Warning, only " + position.size() + " fac build areas");
        }
        buildPositions.clear();
        buildPositions.addAll(position);
        if (facsPlanned == 1) {
            if (Math.min(command.areaManager.getMapWidth(), command.areaManager.getMapHeight()) > 5000) {
                bestFac = clbk.getUnitDefByName("factoryplane");
            } else {
                bestFac = clbk.getUnitDefByName("factorygunship");
            }
            buildPositions.clear();
            buildPositions.addAll(command.areaManager.getAreas());
        }
        if (facsPlanned == 2) {
            // bestFac = clbk.getUnitDefByName("factoryjump"); //hardcode light vehicle factory 
        }
        if (facsPlanned == 3) {
            if (Math.min(command.areaManager.getMapWidth(), command.areaManager.getMapHeight()) > 5000) {
                bestFac = clbk.getUnitDefByName("factorygunship");
            } else {
                bestFac = clbk.getUnitDefByName("factoryplane");
            }
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

    public List<UnitDef> getQueue() {
        List<UnitDef> queue = new ArrayList();
        for (Factory f : factories) {
            queue.addAll(f.getBuildQueue());
        }
        return queue;
    }

    public int getQueuedCount(UnitDef ud) {
        int res = 0;
        for (Factory f : factories) {
            for (UnitDef u : f.getBuildQueue()) {
                if (u.equals(ud)) {
                    res++;
                }
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
        if (frame == 50) {

            int cons = command.getCommanderHandlers().size();
            if (cons > 2) {
                cons--;
            }
            for (int i = 1; i < cons; i++) {
                startsquads.add(squads.get(0));
            }
            startsquads.add(squads.get(1));
            startsquads.add(squads.get(1));
            startsquads.add(squads.get(0));
        }
        if (frame % 7 == 1) {
            if (factories.size() > 10) {
                command.debug(factories.size() + " factories!");
            }
            for (Factory f : factories) {
                if (f.getCurrentTask() == null) {
                    f.buildNextUnit();
                }
            }
        }
        if (frame % 42 == 4) {
            int facsBuilding = 0;
            for (BuildTask bt : command.getBuildTasks()) {
                if (bt.getBuilding().getName().contains("factory")) {
                    facsBuilding++;
                }
            }
            command.debug(command.getBuilderHandler().getAverageMetalIncome() + " > " + 15 * (factories.size() + facsBuilding));
            if (command.getBuilderHandler().getAverageMetalIncome() > 15 * (factories.size() + facsBuilding) + 10 && factories.size() + facsBuilding < 7 && facsBuilding < 2) {
                final Set<Area> facareas = new HashSet();
                final UnitDef fac = getNextFac(facareas);

                BuildTask bt = new BuildTask(fac, command.areaManager.getArea(command.getStartPos()).getNearestArea(command.areaManager.SAFE).getPos(), null, this, clbk, command, 10, -1f, false, new PositionChecker() {
                    @Override
                    public boolean checkPosition(AIFloat3 pos) {
                        return (facareas.contains(command.areaManager.getArea(pos)));
                    }
                });
                command.registerBuildTask(bt);
                command.getBuilderHandler().registerBuildTask(bt);
                command.mark(bt.getPos(), "Building " + bt.getBuilding().getHumanName());
            }
        }
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
                if (command.getCurrentFrame() - currentTask.creationTime > 30 * 60 * 7 || command.getCurrentFrame() - currentTask.creationTime > 30 * 10 && currentTask.getResult() == null) {
                    command.debug("Aborting BuildTask for factory because age and result: " + currentTask.getResult());
                    force = true;
                }
                if (force) {
                    currentTask.cancel();
                    command.getBuilderHandler().unregisterBuildTask(currentTask);
                    currentTask = null;
                } else {
                    return;
                }
            }
            Budget budget = unit.getBuildSpeed() > 0 ? Budget.economy : Budget.offense;
            if (command.economyManager.getRemainingBudget(budget) < 0 && !force && budget == Budget.offense) {
                this.unit.assignTask(new WaitTask(command.getCurrentFrame() + 30, this));
                command.debug(this.unit.getDef().getHumanName() + " waiting for budget");
            } else {
                command.debug(this.unit.getDef().getHumanName() + " building " + unit.getHumanName());
                currentTask = new BuildTask(unit, this.unit.getPos(), 0, budget, this, clbk, command);
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
            if (buildOrders.isEmpty()) {
                //command.debug(unit.getDef().getHumanName() + " idle");
            }
        }

        public List<UnitDef> getBuildOptions() {
            return unit.getDef().getBuildOptions();
        }

        @Override
        public void abortedTask(Task t) {

            if (t instanceof BuildTask) {
                command.getBuilderHandler().unregisterBuildTask(currentTask);
                currentTask = null;
            }
            buildNextUnit();
        }

        @Override
        public void finishedTask(Task t) {
            if (t.equals(currentTask)) {
                buildOrders.poll();
            }
            if (t instanceof BuildTask) {
                command.getBuilderHandler().unregisterBuildTask(currentTask);
                currentTask = null;
            }
            buildNextUnit();
        }

        @Override
        public void reportSpam() {
            throw new RuntimeException("this is so deprecated");
        }

    }

}
