/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.units.tasks;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import com.springrts.ai.oo.clb.UnitDef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import zkcbai.Command;
import zkcbai.UnitCreatedListener;
import zkcbai.UnitDestroyedListener;
import zkcbai.UnitFinishedListener;
import zkcbai.helpers.EconomyManager.Budget;
import zkcbai.helpers.PositionChecker;
import zkcbai.helpers.ZoneManager;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;

/**
 *
 * @author User
 */
public class BuildTask extends Task implements TaskIssuer, UnitFinishedListener, UnitCreatedListener, UnitDestroyedListener {

    UnitDef building;
    Unit result;
    Budget budget;
    boolean resultFinished = false;
    boolean aborted = false;
    AIFloat3 pos;
    int facing;
    OOAICallback clbk;
    List<AITroop> assignedUnits;
    static Command command;

    public static AIFloat3 findClosestBuildSite(UnitDef building, AIFloat3 approxPos, int minDist, int facing, Command command, boolean reachable) {
        return findClosestBuildSite(building, approxPos, minDist, facing, command, -1f, reachable, null);
    }

    public static AIFloat3 findClosestBuildSite(UnitDef building, AIFloat3 approxPos, int minDist, int facing, Command command, boolean reachable, PositionChecker checker) {
        return findClosestBuildSite(building, approxPos, minDist, facing, command, -1f, reachable, checker);
    }

    public static AIFloat3 findClosestBuildSite(UnitDef building, AIFloat3 approxPos, int minDist, int facing, Command command, float heightRange, boolean reachable, PositionChecker checker) {
        return findClosestBuildSite(building, approxPos, minDist, facing, command, heightRange, reachable, checker, -1f);
    }

    /**
     *
     * @param building unitdef
     * @param approxPos position from which to start search
     * @param minDist minimum distance to next building 1 corresponds to 8 elmo
     * @param facing
     * @param heightRange use values > 0 to specify the radius of a circle in which the highest position should be used, instead of using the nearest position
     * @param command instance of Command
     * @param reachable should the construction site be reachable
     * @param checker position checker that returns whether a set of coordinates is an allowed buildpos<br>null means all positions are valid
     * @param searchRange radius of circle in which build positions should be found<br>specify -1 to seach the whole map
     * @return AIFloat(-1, -1, -1) if no valid position was found and a search range was specified
     */
    public static AIFloat3 findClosestBuildSite(UnitDef building, AIFloat3 approxPos, int minDist, int facing, Command command, float heightRange, boolean reachable, PositionChecker checker, float searchRange) {
        final float step = 16;
        float _minDist = 8 * minDist;
        AIFloat3 bestPos = null;
        //command.debug("finding buildsite for "  + building.getHumanName());
        outer:
        for (int radius = 0; radius < 1000; radius++) {
            if (radius * step > searchRange && searchRange > 0) {
                break;
            }
            //command.debug("radius is now " + radius);
            for (int y = -radius; y <= radius; y += Math.max(1, 2 * radius)) {
                for (int x = -radius; x <= radius; x++) {
                    AIFloat3 pos = new AIFloat3(approxPos);
                    pos.add(new AIFloat3(x * step, 0, y * step));
                    if (pos.z < 0 || pos.z > command.areaManager.getMapHeight()) {
                        continue;
                    }
                    if (pos.x < 0 || pos.x > command.areaManager.getMapWidth()) {
                        continue;
                    }
                    pos.y = command.getCallback().getMap().getElevationAt(pos.x, pos.z);
                    if (!command.isPossibleToBuildAt(building, pos, facing)) {
                        continue;
                    }
                    if (reachable && !command.areaManager.getArea(pos).isReachable()) {
                        continue;
                    }
                    AIUnit nearestB = command.areaManager.getNearestBuilding(pos);
                    if (nearestB != null && nearestB.distanceTo(pos) < _minDist + building.getRadius() + nearestB.getDef().getRadius()) {
                        continue;
                    }
                    boolean valid = true;
                    for (BuildTask bt : command.areaManager.getArea(pos).getNearbyBuildTasks(3)) {
                        if (Command.distance2D(bt.getPos(), pos) < _minDist + building.getRadius() + bt.getBuilding().getRadius()) {
                            valid = false;
                        }
                    }
                    if (!valid) {
                        continue;
                    }
                    if (checker != null && !checker.checkPosition(pos)) {
                        continue;
                    }
                    if (bestPos == null || command.getCallback().getMap().getElevationAt(pos.x, pos.z) > command.getCallback().getMap().getElevationAt(bestPos.x, bestPos.z)) {
                        bestPos = pos;
                    }
                    if (radius * step > heightRange) {
                        break outer;
                    }
                }
            }
            for (int x = -radius; x <= radius; x += Math.max(1, 2 * radius)) {
                for (int y = -radius; y <= radius; y++) {
                    AIFloat3 pos = new AIFloat3(approxPos);
                    pos.add(new AIFloat3(x * step, 0, y * step));
                    if (pos.z < 0 || pos.z > command.areaManager.getMapHeight()) {
                        continue;
                    }
                    if (pos.x < 0 || pos.x > command.areaManager.getMapWidth()) {
                        continue;
                    }
                    pos.y = command.getCallback().getMap().getElevationAt(pos.x, pos.z);
                    if (!command.isPossibleToBuildAt(building, pos, facing)) {
                        continue;
                    }
                    if (reachable && !command.areaManager.getArea(pos).isReachable()) {
                        continue;
                    }
                    AIUnit nearestB = command.areaManager.getNearestBuilding(pos);
                    if (nearestB != null && nearestB.distanceTo(pos) < _minDist + building.getRadius() + nearestB.getDef().getRadius()) {
                        continue;
                    }
                    boolean valid = true;
                    for (BuildTask bt : command.areaManager.getArea(pos).getNearbyBuildTasks(3)) {
                        if (Command.distance2D(bt.getPos(), pos) < _minDist + building.getRadius() + bt.getBuilding().getRadius()) {
                            valid = false;
                        }
                    }
                    if (!valid) {
                        continue;
                    }
                    if (checker != null && !checker.checkPosition(pos)) {
                        continue;
                    }
                    if (bestPos == null || command.getCallback().getMap().getElevationAt(pos.x, pos.z) > command.getCallback().getMap().getElevationAt(bestPos.x, bestPos.z)) {
                        bestPos = pos;
                    }
                    if (radius * step > heightRange) {
                        break outer;
                    }
                }
            }
        }
        if (bestPos != null) {
            return bestPos;
        }
        if (searchRange < 0) {
            throw new AssertionError("no valid build pos found");
        }
        return new AIFloat3(-1, -1, -1);
        //return approxPos;
    }

    /**
     * Builds building as close as possible to the specified position while retaining a preset minimum distance to the closest building
     *
     * @param building
     * @param approxPos
     * @param issuer
     * @param clbk
     * @param command
     * @param reachable whether the construction site should be in a reachable area
     */
    public BuildTask(UnitDef building, AIFloat3 approxPos, Budget budget, TaskIssuer issuer, OOAICallback clbk, Command command, boolean reachable) {//simplified constructor
        this(building, approxPos, budget, issuer, clbk, command, 3, reachable);
    }

    /**
     * Builds building as close as possible to the specified position while retaining a minimum distance to the closest building
     *
     * @param building
     * @param approxPos
     * @param issuer
     * @param clbk
     * @param command
     * @param minDist
     * @param reachable whether the construction site should be in a reachable area
     */
    public BuildTask(UnitDef building, AIFloat3 approxPos, Budget budget, TaskIssuer issuer, OOAICallback clbk, Command command, int minDist, boolean reachable) {//simplified constructor
        this(building, findClosestBuildSite(building, approxPos, minDist, (approxPos.z > clbk.getMap().getHeight() * 4) ? 2 : 0, command, -1f, reachable, null),
                (approxPos.z > clbk.getMap().getHeight() * 4) ? 2 : 0, budget, issuer, clbk, command);
    }

    /**
     * Builds building as close as possible to the specified position while retaining a minimum distance to the closest building<br />
     * searches for highest possible buildpos in heightRange
     *
     * @param building
     * @param approxPos
     * @param issuer
     * @param clbk
     * @param command
     * @param minDist
     * @param heightRange radius for search in which to find highest position
     * @param reachable whether the construction site should be in a reachable area
     */
    public BuildTask(UnitDef building, AIFloat3 approxPos, Budget budget, TaskIssuer issuer, OOAICallback clbk, Command command, int minDist, float heightRange, boolean reachable) {//simplified constructor
        this(building, findClosestBuildSite(building, approxPos, minDist, (approxPos.z > clbk.getMap().getHeight() * 4) ? 2 : 0, command, heightRange, reachable, null),
                (approxPos.z > clbk.getMap().getHeight() * 4) ? 2 : 0, budget, issuer, clbk, command);
    }

    /**
     * Builds building as close as possible to the specified position while retaining a minimum distance to the closest building<br />
     * searches for highest possible buildpos in heightRange
     *
     * @param building
     * @param approxPos
     * @param issuer
     * @param clbk
     * @param command
     * @param minDist
     * @param heightRange radius for search in which to find highest position
     * @param reachable whether the construction site should be in a reachable
     * @param checker position checker that returns whether a set of coordinates is an allowed buildpos<br>null means all positions are valid area
     */
    public BuildTask(UnitDef building, AIFloat3 approxPos, Budget budget, TaskIssuer issuer, OOAICallback clbk, Command command, int minDist, float heightRange, boolean reachable, PositionChecker checker) {//simplified constructor
        this(building, findClosestBuildSite(building, approxPos, minDist, (approxPos.z > clbk.getMap().getHeight() * 4) ? 2 : 0, command, heightRange, reachable, checker),
                (approxPos.z > clbk.getMap().getHeight() * 4) ? 2 : 0, budget, issuer, clbk, command);
    }

    /**
     * Builds the specified UnitDef at this exact position
     *
     * @param building
     * @param pos
     * @param facing
     * @param issuer
     * @param clbk
     * @param command
     * @param budget who pays
     * @throws AssertionError if it's not possible to build at the specified location
     */
    public BuildTask(UnitDef building, AIFloat3 pos, int facing, Budget budget, TaskIssuer issuer, OOAICallback clbk, Command command) {
        super(issuer);
        //command.mark(pos, building.getHumanName() +  clbk.getMap().isPossibleToBuildAt(building, pos, facing));
        this.building = building;
        this.pos = pos;
        this.facing = facing;
        this.clbk = clbk;
        this.budget = budget;
        BuildTask.command = command;
        this.lastExecution = command.getCurrentFrame();
        if (building.getSpeed() <= 0 && !command.isPossibleToBuildAt(building, pos, facing)) {
            command.mark(pos, "unable to build here");
            throw new AssertionError("Invalid BuildTask parameters: Unable to build at location");
        }
        assignedUnits = new ArrayList();
        //command.debug("radius of " + building.getHumanName() + " is " + building.getRadius());

        /*try{
         throw new AssertionError("");
         }catch(Throwable t){
         command.debug("started task " + getTaskId(), t);
         }*/
        command.addUnitFinishedListener(this);
        command.addUnitCreatedListener(this);
        command.addUnitDestroyedListener(this);
        if (budget != null) {
            command.economyManager.useBudget(budget, building.getCost(command.metal));
        }
    }

    private void cleanup() {

        if (building.getSpeed() > 0) {
            for (AITroop a : assignedUnits) {
                //a.getUnits().iterator().next().getUnit().stop((short) 0, command.getCurrentFrame() + 100);
                List<Float> cmds = new ArrayList<>();
                for (com.springrts.ai.oo.clb.Command c : a.getUnits().iterator().next().getUnit().getCurrentCommands()) {
                    cmds.add((float) c.getId());
                    //command.debug(c.getId() + "/ " + c.getCommandId() + "command");
                    for (UnitDef ud : command.getCallback().getUnitDefs()) {
                        if (ud.getUnitDefId() == -c.getId()) {
                            //command.debug("def " + ud.getHumanName());
                        }
                    }
                }
                a.getUnits().iterator().next().getUnit().executeCustomCommand(2, cmds, (short) 255, command.getCurrentFrame() + 50);
                //remove from queue
                //command.mark(a.getUnits().iterator().next().getPos(), "stopped");
            }
        }
        //command.debug("cleaning up BuildTask " + getTaskId() + " for " + building.getHumanName());
        command.removeUnitFinishedListener(this);
        command.removeUnitCreatedListener(this);
        command.removeUnitDestroyedListener(this);
        command.clearBuildTask(this);
        List<AIUnit> auc = new ArrayList();
        Collections.copy(assignedUnits, auc);
        //assignedUnits.clear();
        assignedUnits = null;
        for (AIUnit au : auc) {
            if (au.getTask().equals(this)) {
                au.idle();
            }
        }
    }

    List<String> errorMessages = new ArrayList();

    public boolean isDone() {
        return resultFinished || aborted;
    }

    public boolean isAborted() {
        return aborted;
    }

    protected int lastBuildOrderTime = -10000;

    protected int attempts = 0;

    /**
     *
     * @param u AITroop to use for task
     * @return true if task has been finished, false otherwise
     */
    @Override
    public boolean execute(AITroop u) {
        lastExecution = command.getCurrentFrame();
        if (command.areaManager.getArea(pos).getZone() != ZoneManager.Zone.own && command.getCurrentFrame() > 300) {
            command.debug("BuildTask paused because Enemy presence");
            return true;
        }
        if (errors > 10 && building.getCost(command.metal) < 1000) {
            completed(u);
            command.debug("aborted task execution because of errors:");
            for (String s : errorMessages) {
                command.debug(s);
            }
            if (!isAborted()) {
                aborted = true;
                issuer.abortedTask(this);
                cleanup();
            }
            return true;
        }
        if (resultFinished || aborted) {
            //command.debug("aborted task execution because of finished");
            completed(u);
            return true;
        }
        if (result != null && Command.distance2D(pos, result.getPos()) > 65) {
            //command.mark(result.getPos(), "wrong " + building.getHumanName() + ", dist " + Command.distance2D(pos, result.getPos()));
            result = null;
        }
        if (result == null) {
            for (Unit unit : command.getCallback().getFriendlyUnitsIn(pos, 65)) {
                if (unit.isBeingBuilt() && unit.getDef().equals(building)) {
                    result = unit;
                    break;
                }
            }
        }

        if (!assignedUnits.contains(u)) {
            assignedUnits.add(u);
        }

        if (u.distanceTo(pos) > 440) {
            AIFloat3 trg = new AIFloat3();
            trg.interpolate(pos, u.getPos(), 100f / u.distanceTo(pos));

            u.assignTask(new MoveTask(trg, command.getCurrentFrame() + 30 * 5, this, command.pathfinder.AVOID_ENEMIES, command).queue(this));
            return false;
        }
        if (u.distanceTo(pos) > 2 * u.getDef().getBuildDistance() && Math.random() < 0.1) {
            if (attempts++ > 5) {
                u.moveTo(pos, command.getCurrentFrame() + 30 * Math.min((int) Math.abs(clbk.getMap().getElevationAt(pos.x, pos.z) - u.getPos().y), 30 * 40));
            } else {

                u.moveTo(pos, command.getCurrentFrame() + 30);
            }
            return false;
        }
        if (false && u.distanceTo(pos) < building.getRadius() && building.getSpeed() <= 0.001) {
            //command.debug("Inside radius of " + building.getRadius() + " elmos.");
            AIFloat3 tpos = new AIFloat3(u.getPos());
            tpos.sub(this.pos);
            tpos.add(new AIFloat3((float) (Math.random() * 500 - 250), 0, (float) (Math.random() * 500 - 250)));
            tpos.normalize();
            tpos.scale(building.getRadius() * 1.5f);
            tpos.add(pos);
            //command.mark(tpos, "moving to here first");
            u.moveTo(tpos, command.getCurrentFrame() + 50);
            return false;
        }

        if ((building.getSpeed() <= 0 && !command.getCallback().getMap().isPossibleToBuildAt(building, pos, facing)) && result == null) {
            errors++;
            errorMessages.add("Impossible to build at location");
            command.mark(pos, "cant build " + building.getHumanName());
            u.wait(command.getCurrentFrame() + 20);

            return false;
        }
        if (building.getSpeed() > 0.05 && command.getFactoryHandler().getUnits().contains((AIUnit) u)) {
            if (!(result != null && result.getHealth() > 0) && command.getCurrentFrame() - lastBuildOrderTime > 90) {
                u.build(building, facing, pos, AITroop.OPTION_ALT_KEY, command.getCurrentFrame() + 100);
                lastBuildOrderTime = command.getCurrentFrame();
            } else {
                if (command.getCurrentFrame() - lastBuildOrderTime > 90) {
                    //command.mark(result.getPos(), "already building " + building.getHumanName() + " at " + result.getHealth());
                }
                u.wait(command.getCurrentFrame() + 90);
            }
            //command.mark(pos, "building unit " + hashCode());
            /*try{
             throw new RuntimeException();
             }catch(Exception ex){
             command.debug("trace", ex);
             }*/
            return false;
        }
        if (result != null) {
            u.repair(result, command.getCurrentFrame() + 100);
        } else {
            u.build(building, facing, pos, AITroop.OPTION_NONE, command.getCurrentFrame() + 100);
        }

        return false;
    }

    int errors = 0;

    @Override
    public void moveFailed(AITroop u) {
        errors++;
        errorMessages.add("MoveFailed");
    }

    @Override
    public void abortedTask(Task t) {
        errors++;

        errorMessages.add("Aborted MoveTask");
        /*
         try{
         pathFindingError(((MoveTask)t).getLastExecutingUnit());
         }catch(ClassCastException ex){}*/
    }

    @Override
    public BuildTask clone() {
        BuildTask as = new BuildTask(building, pos, facing, budget, issuer, clbk, command);
        as.result = this.result;
        as.queued = this.queued;
        return as;
    }

    public UnitDef getBuilding() {
        return building;
    }

    public AIFloat3 getPos() {
        return pos;
    }

    public void removeWorker(AITroop worker) {
        if (assignedUnits == null) {
            return;
        }
        assignedUnits.remove(worker);
    }

    public Collection<AITroop> getWorkers() {
        return assignedUnits;
    }

    @Override
    public Object getResult() {
        return result;
    }

    @Override
    public void finishedTask(Task t) {
    }

    @Override
    public void unitFinished(AIUnit u) {

        /*if (building.getHumanName().equalsIgnoreCase("wasp")){
            command.debug("wasp-finished " + u.getDef().getHumanName());
        }*/
 /*if (u.getDef().equals(building) && building.getSpeed() > 0.1){
            command.mark(u.getPos(), "finished " + u.getUnit().getUnitId() +  " = " + (result != null ? result.getUnitId() : -1) + " dist: " + u.distanceTo(pos));
        }*/
        if ((u.getUnit().getDef().equals(building) && u.distanceTo(pos) < 65) || (result != null && result.getUnitId() == u.getUnit().getUnitId())) {
            command.debug("finished " + building.getHumanName() + ": " + u.getUnit().getUnitId());

            completed(u);
            result = u.getUnit();
            issuer.finishedTask(this);
            resultFinished = true;
            cleanup();
        }
    }

    @Override
    public void reportSpam() {
        throw new RuntimeException("I spammed MoveTasks!");
    }

    @Override
    public void unitCreated(Unit u, AIUnit builder) {
        if (u.getDef().equals(building) && Command.distance2D(pos, u.getPos()) < 65 && result == null) {
            //command.mark(u.getPos(), "unit created " + hashCode());
            result = u;
        }
    }

    @Override
    public void cancel() {
        //command.debug("Cancelled BuildTask " + getTaskId() +" for " + building.getHumanName());
        aborted = true;
        cleanup();
    }

    @Override
    public void unitDestroyed(Unit u, Enemy killer) {
        if (u.equals(result)) {
            result = null;
        }
    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.BuildTask;
    }

    @Override
    public void unitDestroyed(AIUnit u, Enemy e) {
        assignedUnits.remove(u);
    }
}
