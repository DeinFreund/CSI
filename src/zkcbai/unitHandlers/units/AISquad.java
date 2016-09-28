/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.units;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.Unit;
import com.springrts.ai.oo.clb.UnitDef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import zkcbai.UnitDestroyedListener;
import zkcbai.UpdateListener;
import zkcbai.unitHandlers.DevNullHandler;
import zkcbai.unitHandlers.units.tasks.Task;
import zkcbai.utility.Point;
import zkcbai.utility.SmallestEnclosingCircle;

/**
 *
 * @author User
 */
public class AISquad extends AITroop implements AIUnitHandler, UpdateListener, UnitDestroyedListener, RepairListener {

    Set<AIUnit> allUnits = new HashSet();
    Set<AIUnit> activeUnits = new HashSet(); //units that are not busy being repaired
    private int wakeUpFrame = -1;
    private float minRange = Float.MAX_VALUE;
    private float minSlope = Float.MAX_VALUE;
    private Set<AIUnit> idlers = new HashSet();
    private boolean dead = false;
    private int timeCreated;
    private boolean autoMerge;
    
    private static List<AISquad> squads = new ArrayList();

    
    public AISquad(AIUnitHandler handler) {
        this(handler, false);
    }
    
    /**
     *
     * @param handler
     * @param autoMerge whether squad should automatically merge with nearby squads
     */
    public AISquad(AIUnitHandler handler, boolean autoMerge) {
        super(handler);
        handler.getCommand().addUnitDestroyedListener(this);
        squads.add(this);
        timeCreated = handler.getCommand().getCurrentFrame();
        this.autoMerge = false;
        reachableAreas = new HashSet();
    }

    public void addUnit(AIUnit u) {
        allUnits.add(u);
        activeUnits.add(u);
        u.addRepairListener(this);
        u.assignAIUnitHandler(this);
        minRange = Math.min(u.getMaxRange(), minRange);
        minSlope = Math.min(minSlope, u.getMaxSlope());
        dead = false;
        
        reachableAreas = u.getReachableAreas();
    }

    public void removeUnit(AIUnit u, AIUnitHandler newHandler) {
        if (!allUnits.contains(u)) {
            return;
        }
        u.assignAIUnitHandler(newHandler);
        u.removeRepairListener(this);
        allUnits.remove(u);
        activeUnits.remove(u);
        minRange = Float.MAX_VALUE;
        minSlope = Float.MAX_VALUE;
        for (AIUnit au : allUnits) {
            minRange = Math.min(au.getMaxRange(), minRange);
            minSlope = Math.min(minSlope, au.getMaxSlope());
        }
        if (allUnits.isEmpty()) {
            dead = true;
            squads.remove(this);
            if (getCommand() != null) {
                getCommand().removeUnitDestroyedListener(this);
            }
        }
        //*/
    }

    @Override
    public float distanceTo(AIFloat3 trg) {
        AIFloat3 pos = new AIFloat3(getPos());
        pos.sub(trg);
        pos.y = 0;
        //if (getRadius() > 40) handler.getCommand().mark(getPos(), "regrouping");
        return pos.length();// + Math.max(0,getRadius()-50);
    }

    /**
     * Used to determine how far the squad is spread out
     *
     * @return
     */
    public float getRadius() {
        List<Point> positions = new ArrayList();
        Collection<AIUnit> units = activeUnits;
        if (units.isEmpty()) units = allUnits;
        for (AIUnit au : units) {
            positions.add(new Point(au.getPos().x, au.getPos().z));
        }
        return (float) SmallestEnclosingCircle.makeCircle(positions).r;
    }

    @Override
    public AIFloat3 getPos() {
        List<Point> positions = new ArrayList();
        List<Float> xpos = new ArrayList();
        List<Float> ypos = new ArrayList();
        Collection<AIUnit> units = activeUnits;
        if (units.isEmpty()) units = allUnits;
        AIFloat3 position = new AIFloat3();
        for (AIUnit au : units) {
            position.add(au.getPos());
            positions.add(new Point(au.getPos().x, au.getPos().z));
            xpos.add(au.getPos().x);
            ypos.add(au.getPos().z);
        }
        Collections.sort(xpos);
        Collections.sort(ypos);
        position.scale(1f / units.size());
        Point p = SmallestEnclosingCircle.makeCircle(positions).c;
        AIFloat3 pos = new AIFloat3((float) xpos.get(xpos.size() / 2), 0, (float) ypos.get(ypos.size() / 2));
        pos.y = handler.getCommand().getCallback().getMap().getElevationAt(pos.x, pos.z);
        return pos;
    }
    
    @Override
    public UnitDef getDef(){
        Map<UnitDef, Integer> cnt = new TreeMap();
        for (AIUnit u : allUnits){
            if (!cnt.containsKey(u.getDef())) cnt.put(u.getDef(), 0);
            cnt.put(u.getDef(), cnt.get(u.getDef()) + 1);
        }
        
        int max = -1;
        UnitDef res = null;
        for (Map.Entry<UnitDef, Integer> e :  cnt.entrySet()){
            if (e.getValue() > max){
                max = e.getValue();
                res = e.getKey();
            }
        }
        if (res == null) throw new RuntimeException("Empty Squad -> no UnitDef");
        return res;
    }

    @Override
    public void update(int frame) {
        if (dead) {
            return;
        }
        idle();
    }

    private void clearUpdateListener() {
        if (handler.getCommand() != null && wakeUpFrame > handler.getCommand().getCurrentFrame()) {
            handler.getCommand().removeSingleUpdateListener(this, wakeUpFrame);
            wakeUpFrame = -1;
        }
    }

    @Override
    protected void doTask() {
        if (handler.getCommand().getCurrentFrame() - timeCreated > 150 && autoMerge) {
            Map<Integer, List<AIUnit>> unittypes = new TreeMap();
            int squadcount = 0;
            List<Task> tasks = new ArrayList();
            List<AIUnit> units = new ArrayList();
            AIFloat3 pos = getPos();
            for (AISquad s : squads.toArray(new AISquad[squads.size()])) {
                if (handler.getCommand().getCurrentFrame() - s.timeCreated > 150 && s.distanceTo(pos) < 600 && handler.equals(s.handler)) {
                    squadcount++;
                    if (s.getTask() != null) {
                        tasks.add(s.getTask());
                    }
                    for (AIUnit u : s.getUnits().toArray(new AIUnit[s.getUnits().size()])) {
                        units.add(u);
                        s.removeUnit(u, null);
                        if (!unittypes.containsKey(u.getDef().getUnitDefId())) {
                            unittypes.put(u.getDef().getUnitDefId(), new ArrayList());
                        }
                        unittypes.get(u.getDef().getUnitDefId()).add(u);
                    }
                }
            }
            while (!unittypes.isEmpty()) {
                squadcount--;
                List<AIUnit> big = null;
                if (squadcount > 0) {
                    int bigi = 0;
                    for (Map.Entry<Integer, List<AIUnit>> e : unittypes.entrySet()) {
                        if (big == null || e.getValue().size() > big.size()) {
                            big = e.getValue();
                            bigi = e.getKey();
                        }
                    }
                    unittypes.remove(bigi);
                } else {
                    big = new ArrayList();
                    for (List<AIUnit> l : unittypes.values()) {
                        big.addAll(l);
                    }
                    unittypes.clear();
                }
                AISquad ns = new AISquad(handler);
                for (AIUnit au : big) {
                    ns.addUnit(au);
                }
                if (!tasks.isEmpty()) {
                    Task task = tasks.get(0).clone();
                    for (int i = 1; i < tasks.size(); i++) {
                        task.queue(tasks.get(i).clone());
                    }
                    ns.assignTask(task);
                }
            }
            for ( AIUnit au : units ) {
                if (au.handler == null) throw new AssertionError("No AIUnitHandler");
            }
        }
        
        if (dead) {
            getCommand().debug("AITroop (" + getDef().getHumanName()+ ") not executing task because dead");
            return;
        }
        
        super.doTask();
    }

    /**
     * Will only be called if all units of squad are idle
     */
    @Override
    public void idle() {
        idlers.clear();
        clearUpdateListener();
        doTask();
    }

    @Override
    public Collection<AIUnit> getUnits() {
        return allUnits;
    }

    @Override
    public void moveTo(AIFloat3 trg, short options, int timeout) {
        //handler.getCommand().mark(trg, "move");
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        trg = new AIFloat3(trg);
        AIFloat3 toTarget = new AIFloat3(trg);
        toTarget.sub(getPos());
        AIFloat3 ortho = new AIFloat3(toTarget.z, toTarget.y, - toTarget.x);
        ortho.normalize();
        float offset = getDef().getSpeed() * 8 / (5 + getUnits().size());
        ortho.scale(offset * ((activeUnits.size() ) / 2));
        trg.sub(ortho);
        ortho.normalize();
        ortho.scale(offset);
        for (AIUnit u : activeUnits) {
            u.moveTo(new AIFloat3(trg), options, Integer.MAX_VALUE);
            trg.add(ortho);
        }
        if (timeout < wakeUpFrame || wakeUpFrame <= getCommand().getCurrentFrame()) {
            clearUpdateListener();
            if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
                wakeUpFrame = timeout;
            }
        }
    }

    @Override
    public void attack(Enemy trg, short options, int timeout) {
        //handler.getCommand().mark(trg, "move");
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        areaManager.executedCommand();
        for (AIUnit u : activeUnits) {
            u.attack(trg, options, Integer.MAX_VALUE);
        }
        if (timeout < wakeUpFrame || wakeUpFrame <= getCommand().getCurrentFrame()) {
            clearUpdateListener();
            if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
                wakeUpFrame = timeout;
            }
        }
    }
    
    @Override
    public void repair(Unit trg, short options, int timeout) {
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        areaManager.executedCommand();
        for (AIUnit u : activeUnits) {
            u.repair(trg, options, Integer.MAX_VALUE);
        }
        if (timeout < wakeUpFrame || wakeUpFrame <= getCommand().getCurrentFrame()) {
            clearUpdateListener();
            if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
                wakeUpFrame = timeout;
            }
        }
    }
    
    @Override
    public void loadUnit(Unit trg, short options, int timeout) {
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        areaManager.executedCommand();
        for (AIUnit u : activeUnits) {
            u.loadUnit(trg, options, Integer.MAX_VALUE);
        }
        if (timeout < wakeUpFrame || wakeUpFrame <= getCommand().getCurrentFrame()) {
            clearUpdateListener();
            if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
                wakeUpFrame = timeout;
            }
        }
    }
    
    
    @Override
    public void reclaimArea(AIFloat3 pos, float radius, short options, int timeout) {
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        areaManager.executedCommand();
        for (AIUnit u : activeUnits) {
            u.reclaimArea(pos, radius, options, Integer.MAX_VALUE);
        }
        if (timeout < wakeUpFrame || wakeUpFrame <= getCommand().getCurrentFrame()) {
            clearUpdateListener();
            if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
                wakeUpFrame = timeout;
            }
        }
    }

    @Override
    public void patrolTo(AIFloat3 trg, short options, int timeout) {
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        areaManager.executedCommand();
        for (AIUnit u : activeUnits) {
            u.patrolTo(trg, options, Integer.MAX_VALUE);
        }
        if (timeout < wakeUpFrame || wakeUpFrame <= getCommand().getCurrentFrame()) {
            clearUpdateListener();
            if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
                wakeUpFrame = timeout;
            }
        }
    }
    
    @Override
    public void fireDGun(AIFloat3 trg, short options, int timeout) {
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        areaManager.executedCommand();
        for (AIUnit u : activeUnits) {
            u.fireDGun(trg, options, Integer.MAX_VALUE);
        }
        if (timeout < wakeUpFrame || wakeUpFrame <= getCommand().getCurrentFrame()) {
            clearUpdateListener();
            if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
                wakeUpFrame = timeout;
            }
        }
    }

    @Override
    public void dropPayload(short options, int timeout) {
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        areaManager.executedCommand();
        for (AIUnit u : activeUnits) {
            u.dropPayload(options, Integer.MAX_VALUE);
        }
        if (timeout < wakeUpFrame || wakeUpFrame <= getCommand().getCurrentFrame()) {
            clearUpdateListener();
            if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
                wakeUpFrame = timeout;
            }
        }
    }
    
    @Override
    public void wait(int timeout) {
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        for (AIUnit u : activeUnits){
            u.wait(Integer.MAX_VALUE);
        }
        if (timeout < wakeUpFrame || wakeUpFrame <= getCommand().getCurrentFrame()) {
            clearUpdateListener();
            if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
                wakeUpFrame = timeout;
            }
        }
    }


    @Override
    public void fight(AIFloat3 trg, short options, int timeout) {
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        areaManager.executedCommand();
        for (AIUnit u : activeUnits) {
            u.fight(trg, options, Integer.MAX_VALUE);
        }
        if (timeout < wakeUpFrame || wakeUpFrame <= getCommand().getCurrentFrame()) {
            clearUpdateListener();
            if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
                wakeUpFrame = timeout;
            }
        }
    }

    
    @Override
    public void attackGround(AIFloat3 trg, short options, int timeout) {
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        areaManager.executedCommand();
        for (AIUnit u : activeUnits) {
            u.attackGround(trg, options, Integer.MAX_VALUE);
        }
        if (timeout < wakeUpFrame || wakeUpFrame <= getCommand().getCurrentFrame()) {
            clearUpdateListener();
            if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
                wakeUpFrame = timeout;
            }
        }
    }


    @Override
    public void build(UnitDef building, int facing, AIFloat3 trg, short options, int timeout
    ) {
        //handler.getCommand().mark(trg, "build " + building.getHumanName());
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        areaManager.executedCommand();
        for (AIUnit u : activeUnits) {
            u.build(building, facing, trg, options, Integer.MAX_VALUE);
        }

        if (timeout < wakeUpFrame || wakeUpFrame <= getCommand().getCurrentFrame()) {
            clearUpdateListener();
            if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
                wakeUpFrame = timeout;
            }
        }
    }
    
    @Override
    public void setTarget(int targetUnitId) {
        
        areaManager.executedCommand();
        for (AIUnit u : activeUnits) {
            u.setTarget(targetUnitId);
        }
    }


    @Override
    public void troopIdle(AITroop u) {
        idlers.add((AIUnit) u);
        if (idlers.size() == allUnits.size()) {
            String tasks = "";
            for (Task t : taskqueue){
                tasks += t.toString()+";";
            }
            //getCommand().debug("all idle doing " + task.toString() +";" + tasks);
            idle();
        }else{
            u.wait(handler.getCommand().getCurrentFrame() + 30);
        }
    }

    @Override
    public float getMaxRange() {
        return minRange;
    }

    @Override
    public float getMaxSlope() {
        return minSlope;
    }

    @Override
    public void unitDestroyed(AIUnit u, Enemy e) {
        removeUnit(u, new DevNullHandler(getCommand(), getCommand().getCallback()));
    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {
    }

    @Override
    public float getEfficiencyAgainst(Enemy e) {
        return getEfficiencyAgainst(e.getDef());
    }

    @Override
    public float getEfficiencyAgainst(UnitDef ud) {
        float tot = 0.1f;
        float div = 0.1f;
        for (AIUnit u : allUnits){
            tot += u.getEfficiencyAgainst(ud)*u.getDef().getCost(handler.getCommand().metal);
            div += u.getDef().getCost(handler.getCommand().metal);
        }
        return tot/div;
    }

    @Override
    public boolean retreatForRepairs(AITroop u) {
        return handler.retreatForRepairs(this);
    }

    @Override
    public void retreating(AIUnit u) {
        activeUnits.remove(u);
    }

    @Override
    public void finishedRepairs(AIUnit u) {
        activeUnits.add(u);
    }

    
    @Override
    public void unitDestroyed(Unit u, Enemy e) {
        
    }
}
