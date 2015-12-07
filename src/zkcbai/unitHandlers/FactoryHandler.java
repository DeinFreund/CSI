/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers;

import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import com.springrts.ai.oo.clb.UnitDef;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import zkcbai.Command;
import zkcbai.UpdateListener;
import zkcbai.helpers.Pathfinder;
import zkcbai.helpers.Pathfinder.MovementType;
import zkcbai.helpers.ZoneManager.Area;
import zkcbai.unitHandlers.units.AISquad;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.BuildTask;
import zkcbai.unitHandlers.units.tasks.Task;

/**
 *
 * @author User
 */
public class FactoryHandler extends UnitHandler implements UpdateListener {


    private Set<UnitDef> builtFacs = new HashSet();
    private int constructorRequests = 0;

    public FactoryHandler(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        //cmd.addSingleUpdateListener(this, assaultFrame);
    }

    @Override
    public AIUnit addUnit(Unit u) {
        AIUnit au = new AIUnit(u, this);
        aiunits.put(u.getUnitId(), au);
        builtFacs.add(u.getDef());
        
        
        recalculateReachable(); //this call is only for gui
        return au;
    }

    BuildTask lastCaretakerRequest;
    
    @Override
    public void troopIdle(AIUnit u) {
        //if (!u.getUnit().getCurrentCommands().isEmpty()) throw new AssertionError("Unit not really idle");
        if (constructorRequests > 0) {

            for (UnitDef ud : u.getDef().getBuildOptions()) {
                if (!ud.getBuildOptions().isEmpty()) {
                    u.assignTask(new BuildTask(ud, u.getPos(), 0, this, clbk, command));
                    constructorRequests--;
                    return;
                }
            }
        }

        if (command.economyManager.getRemainingOffenseBudget() > 0) {

            float unitDensity = 1000000 * command.getFighterHandler().getFighters().size() / 
                    command.getCallback().getMap().getWidth() * command.getCallback().getMap().getHeight();
            
            final float targetDensity = 1;
            float efficiencyMult = 1f / (-Math.min(0,(float)Math.log(unitDensity/targetDensity + 1e-6)) + 1);
            command.debug("Unit density is " + unitDensity + " / " + targetDensity + " -> mult: "  + efficiencyMult);
            
            if (command.economyManager.getRemainingOffenseBudget() > 500 && (lastCaretakerRequest == null || lastCaretakerRequest.getResult() != null)
                    && command.getCurrentFrame() > 1000) {
                lastCaretakerRequest = command.getBuilderHandler().requestCaretaker(u.getPos());
            }
            Enemy worst = null;
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
            if (best != null) {
                command.debug("Best counter is " + best.getHumanName());
                u.assignTask(new BuildTask(best, u.getPos(), 0, this, clbk, command));
                command.economyManager.useOffenseBudget(best.getCost(command.metal));
                return;
            }
            u.wait(command.getCurrentFrame() + 30);
        }
                
    }
    
    protected void recalculateReachable(){
        for (Area a : command.areaManager.getAreas()){
            a.setReachable(false);
        }
        for (AIUnit au : aiunits.values()){
            MovementType mt = Pathfinder.MovementType.getMovementType(au.getDef().getBuildOptions().get(0));
            for (Area a : command.areaManager.getArea(au.getPos()).getConnectedAreas(mt)){
                a.setReachable();
            }
        }
    }
    

    public void requestConstructor() {
        constructorRequests = 1;
    }

    @Override
    public void abortedTask(Task t) {
    }

    @Override
    public void finishedTask(Task t) {
    }
    
    protected float getMovementTypeMultiplier(MovementType mt){
        switch (mt){
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

    /**
     *
     * @param buildPositions container in which the possible building areas are returned<br />will be cleared
     * @return the UnitDef of the best factory
     */
    public UnitDef getNextFac(Set<Area> buildPositions) {
        recalculateReachable();
        UnitDef bestFac = null;
        Set<Area> position = null;
        float bestScore = -1;
        
        for (UnitDef ud : command.getCallback().getUnitDefByName("armrectr").getBuildOptions()){
            boolean allMovable = ud.getBuildOptions().size() > 0;
            for (UnitDef buildable : ud.getBuildOptions()){
                if (buildable.getSpeed() < 0.1){
                    allMovable = false;
                }
            }
            if (allMovable){//is factory
                command.debug(ud.getHumanName() + " is a fac");
                MovementType mt = MovementType.getMovementType(ud.getBuildOptions().get(0));
                Set<Area> bestset = new HashSet();
                int bestsize = 0;
                Set<Area> totset = new HashSet();
                for (Area a : command.areaManager.getAreas()){
                    if (totset.contains(a)) continue;
                    Set<Area> set = a.getConnectedAreas(mt);
                    int size = set.size();
                    for (Area area : set){
                        if (area.isReachable()) size --;
                    }
                    if (size > bestset.size()){
                        bestset = set;
                        bestsize = size;
                    }
                    totset.addAll(set);
                }
                float score = ((float)Math.random()/20f+1f) * bestsize * getMovementTypeMultiplier(mt);
                if (score > bestScore){
                    bestScore = score;
                    bestFac = ud;
                    position = bestset;
                }
            }
        }
        if (bestFac == null){
            throw new AssertionError("Didn't find any valid fac to build");
        }
        buildPositions.clear();
        buildPositions.addAll(position);
        return bestFac;
    }
    
    public Collection<AIUnit> getFacs(){
        return aiunits.values();
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

}
