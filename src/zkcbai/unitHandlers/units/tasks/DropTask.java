/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.units.tasks;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.Unit;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import zkcbai.Command;
import zkcbai.UnitDestroyedListener;
import zkcbai.helpers.AreaChecker;
import zkcbai.helpers.ZoneManager;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.AIUnitHandler;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.FakeEnemy;

/**
 *
 * @author User
 */
public class DropTask extends Task implements TaskIssuer, UnitDestroyedListener, AIUnitHandler {

    private Enemy target;
    //private AIUnit payload;
    private int errors = 0;
    private Set<AIUnit> workers = new HashSet<>();
    private Command command;
    private boolean finished = false;
    private boolean loaded = false;
    private boolean dropped = false;
    private AIFloat3 home;
    //private AIUnit avenger = null;
    private AIUnit gnat = null;
    private final float G;

    public DropTask(Enemy target, TaskIssuer issuer, Command command) {
        super(issuer);
        if (target == null) {
            throw new NullPointerException();
        }
        command.debug("Initialized DropTask on " + target.getDef().getHumanName());
        this.target = target;
        this.command = command;
        home = command.getFactoryHandler().getFacs().iterator().next().unit.getPos();
        command.addUnitDestroyedListener(this);
        G = command.getCallback().getMap().getGravity();
    }

    @Override
    public boolean execute(AITroop u) {
        if (finished) {
            return true;
        }

        workers.add((AIUnit) u);
        if (errors > 10 || target == null || command.getDropHandler().getPayload((AIUnit) u) == null) {
            command.debug("Aborted DropTask");
            issuer.abortedTask(this);
            completed(u);
            return true;
        }/*
        if (!loaded) {
            u.assignTask(new LoadUnitTask(payload, this, command).queue(this));
            return false;
        }*/
        if (dropped) {
            /*if (avenger != null) {
                command.getAvengerHandler().addUnit(avenger);
                avenger = null;
            }*/
            if (gnat != null) {
                command.getDropHandler().addUnit(gnat);
                gnat = null;
            }
            if (u.distanceTo(home) > 500) {
                u.assignTask(new MoveTask(home, command.getCurrentFrame() + 60, this, command.pathfinder.AVOID_ANTIAIR, command).queue(this));
                return false;
            } else {
                issuer.finishedTask(this);
                completed(u);
                return true;
            }
        }
        /*if (avenger == null && command.getDropHandler().getPayload((AIUnit) u).getDef().equals(command.getDropHandler().SKUTTLE)) {
            if (target.timeSinceLastSeen() > 30 * 10) {
                command.getAvengerHandler().requestScout(command.areaManager.getArea(target.getPos()));
            }
            avenger = command.getAvengerHandler().getUnits().iterator().next();
            for (AIUnit au : command.getAvengerHandler().getUnits()) {
                if (au.getUnit().getHealth() > avenger.getUnit().getHealth()) {
                    avenger = au;
                }
            }
            command.getAvengerHandler().removeUnit(avenger);
            avenger.assignAIUnitHandler(this);
            avenger.setRetreat(AIUnit.RetreatState.RetreatOff);
            command.debug("DropTask" + getTaskId() + " acquired avenger " + avenger.getUnit().getUnitId());
        }*/
        if (gnat == null && command.getDropHandler().getPayload((AIUnit) u).getDef().equals(command.getDropHandler().SKUTTLE)) {
            gnat = command.getDropHandler().getUnits().iterator().next();
            for (AIUnit au : command.getDropHandler().getUnits()) {
                if (au.getUnit().getHealth() > gnat.getUnit().getHealth()) {
                    gnat = au;
                }
            }
            command.getDropHandler().removeUnit(gnat);
            gnat.assignAIUnitHandler(this);
            gnat.setRetreat(AIUnit.RetreatState.RetreatOff);
            command.debug("DropTask" + getTaskId() + " acquired gnat " + gnat.getUnit().getUnitId());
        }
        AIFloat3 extrapolated = new AIFloat3(u.getPos()); //vel per frame
        AIFloat3 vel = new AIFloat3(u.getUnits().iterator().next().getUnit().getVel());
        AIFloat3 pos = new AIFloat3(vel);
        pos.scale(command.getCommandDelay());

        command.debug("movement during lag is " + pos.length() + " elmos");
        pos.add(u.getPos());

        float time = 10000;
        float timeo = 0;
        int frames = 0;
        AIFloat3 velmul = new AIFloat3(vel);
        final int framestep = 10;
        velmul.scale(framestep);

        extrapolated.sub(velmul);
        frames -= framestep;
        while (time > u.distanceTo(extrapolated) / vel.length() && frames < 300) {
            extrapolated.add(velmul);
            float yOff = command.getCallback().getMap().getElevationAt(extrapolated.x, extrapolated.z) - pos.y;
            //timeo = (float) Math.sqrt(Math.max(-2 * (-yOff) / G, 0f));
            time = (float) (-vel.y - Math.sqrt(vel.y * vel.y + 2 * G * yOff)) / G;
            frames += framestep;
        }
        AIFloat3 tpos;
        if (gnat == null) tpos = new AIFloat3(target.getPos());
        else tpos = new AIFloat3(target.getLastAccuratePos());
        AIFloat3 tvel = new AIFloat3(target.getVel());
        tvel.scale(time);
        tpos.add(tvel);
        //command.mark(extrapolated, "projected landing pos after "+  frames + " frames");
        //command.debug(timeo + " | " + time);
        AIFloat3 approx = new AIFloat3(extrapolated);
        extrapolated = new AIFloat3(vel);
        extrapolated.scale(time);
        extrapolated.add(pos);
        AIFloat3 toTarget = new AIFloat3(tpos);
        toTarget.sub(pos);
        toTarget.normalize();
        AIFloat3 mirrored = new AIFloat3(toTarget);
        mirrored.scale(2 * toTarget.dot(vel));
        mirrored.sub(vel);
        mirrored.normalize();
        AIFloat3 correction = new AIFloat3(toTarget);
        correction.scale(mirrored.dot(toTarget));
        correction.negate();
        correction.add(mirrored);
        correction.normalize();
        correction.scale(2 * Command.distance2D(tpos, pos) / (command.getCommandDelay() + 2));
        correction.add(tpos);
        mirrored.scale(500);
        mirrored.add(pos);
        AIFloat3 nextframe = new AIFloat3(extrapolated);
        nextframe.add(vel);
        command.debug("drop deviation: " + Command.distance2D(tpos, extrapolated));
        if (Command.distance2D(tpos, extrapolated) < 50f + command.getCommandDelay() * 3 && Command.distance2D(tpos, extrapolated) < Command.distance2D(tpos, nextframe)
                || u.getHealth() / u.getDef().getHealth() < 0.2) {
            u.dropPayload(command.getCurrentFrame() + 10);
            command.debug("drop deviation: " + Command.distance2D(tpos, extrapolated));
            command.debug("last pos: " + (command.getCurrentFrame() - target.getLastAccuratePosTime()));
            command.mark(approx, "approx");
            command.mark(extrapolated, "drop");
            command.mark(target.getPos(), "target");
            command.mark(tpos, "tpredict");
            command.mark(target.getLastAccuratePos(), "tacc");
            dropped = true;
            u.moveTo(home, command.getCurrentFrame() + 10);
            return false;
        }

        command.debug("Distance to drop: " + target.distanceTo(u.getPos()));
        /*if (avenger != null && target.distanceTo(u.getPos()) < 2400f) {
            avenger.assignTask(new WaitTask(command.getCurrentFrame() + 40, this));
            avenger.moveTo(target.getPos(), command.getCurrentFrame() + 40);
            if ( target.distanceTo(u.getPos()) < 1600f){
            avenger.dropPayload(command.getCurrentFrame() + 40);
            }
        } else if (avenger != null) {
            avenger.assignTask(new MoveTask(command.areaManager.getArea(target.getPos()).getNearestArea(new AreaChecker() {
                @Override
                public boolean checkArea(ZoneManager.Area a) {
                    return a.getZone() == ZoneManager.Zone.own && a.getAADPS() < 40;
                }
            }).getPos(), command.getCurrentFrame() + 33, this, command.pathfinder.AVOID_ANTIAIR, command));
        }*/
        if (gnat != null) {
            if (target.distanceTo(u.getPos()) < 2500f) {
                gnat.attack(target, command.getCurrentFrame() + 60);
            } else {
                AIFloat3 gnatmove = new AIFloat3(vel);
                gnatmove.normalize();
                gnatmove.scale(900);
                gnatmove.add(pos);
                gnat.moveTo(gnatmove, command.getCurrentFrame() + 60);
            }
        }
        if (target.distanceTo(u.getPos()) < 1200f) {

            if (vel.length() > 1){
                u.moveTo(correction, command.getCurrentFrame() + 1);
            }else{
                u.moveTo(tpos, command.getCurrentFrame() + 1);
            }
            //u.dropPayload(command.getCurrentFrame() + 15);
            //u.getUnits().iterator().next().getUnit().unload(mpos, payload.getUnit(), (short)0, Integer.MAX_VALUE);
        } else {
            if (gnat != null) {
                if (gnat.distanceTo(u.getPos()) > 600) {
                    u.getUnits().iterator().next().getUnit().setWantedMaxSpeed(u.getDef().getSpeed(), (short) 0, Integer.MAX_VALUE);
                } else {
                    u.getUnits().iterator().next().getUnit().setWantedMaxSpeed(0.1f * u.getDef().getSpeed(), (short) 0, Integer.MAX_VALUE);
                }
            }

            u.assignTask(new MoveTask(target.getPos(), command.getCurrentFrame() + 15, this, command.pathfinder.AVOID_ANTIAIR, command).queue(this));
        }
        return false;
    }

    public Enemy getTarget() {
        return target;
    }

    public Collection<AIUnit> getWorkers() {
        return workers;
    }

    @Override
    public void moveFailed(AITroop u) {
        errors++;
    }

    @Override
    public DropTask clone() {
        DropTask as = new DropTask(target, issuer, command);
        as.queued = this.queued;
        return as;
    }

    @Override
    public Object getResult() {
        return null;
    }

    @Override
    public void cancel() {

    }

    @Override
    public void completed(AITroop au) {

        command.debug("Completed DropTask");
        command.removeUnitDestroyedListener(this);
        workers.clear();
        finished = true;
        super.completed(au);
    }

    @Override
    public void abortedTask(Task t) {
        errors++;
    }

    @Override
    public void finishedTask(Task t) {
        loaded = true;
        //k
    }

    @Override
    public void reportSpam() {
        throw new AssertionError("cmon this shouldnt happen");
    }

    @Override
    public void unitDestroyed(AIUnit u, Enemy killer) {
        /*if (payload.equals(u)){
            errors += 100;
        }*//*
        if (u.equals(avenger)) {
            command.debug("DropTask: Avenger destroyed, nulling");
            avenger = null;
        }*/
        if (u.equals(gnat)) {
            command.debug("DropTask: Gnat destroyed, nulling");
            gnat = null;
        }
    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {
        if (target.equals(e)) {
            errors += 100;
        }
    }

    @Override
    public Command getCommand() {
        return command;
    }

    @Override
    public void troopIdle(AITroop u) {
        u.wait(command.getCurrentFrame() + 50);
    }

    @Override
    public boolean retreatForRepairs(AITroop u) {
        return false;
    }
    
    
    
    @Override
    public void unitDestroyed(Unit u, Enemy e) {
        
    }

}
