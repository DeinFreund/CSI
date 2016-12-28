/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers;

import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import java.util.HashSet;
import java.util.Set;
import zkcbai.Command;
import zkcbai.EnemyDiscoveredListener;
import zkcbai.EnemyEnterLOSListener;
import zkcbai.UnitFinishedListener;
import zkcbai.UpdateListener;
import zkcbai.helpers.ZoneManager.Area;
import zkcbai.helpers.ZoneManager.Zone;
import zkcbai.unitHandlers.units.AISquad;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.MoveTask;
import zkcbai.unitHandlers.units.tasks.Task;

/**
 *
 * @author User
 */
public class BansheeHandler extends UnitHandler implements UpdateListener, EnemyDiscoveredListener, EnemyEnterLOSListener, UnitFinishedListener {

    protected AISquad banshees = new AISquad(this);
    protected float bansheeDPS = 0;
    private Set<AIUnit> repairing = new HashSet();

    public BansheeHandler(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        cmd.addUpdateListener(this);
        command.addEnemyDiscoveredListener(this);
        command.addEnemyEnterLOSListener(this);
        command.addUnitFinishedListener(this);
    }

    @Override
    public AIUnit addUnit(Unit u) {
        AIUnit au = new AIUnit(u, this);
        aiunits.put(u.getUnitId(), au);
        banshees.addUnit(au);
        bansheeDPS += au.getDPS();
        au.setLandWhenIdle(false);
        au.setAutoRepair(false);

        troopIdle(au);
        return au;
    }

    @Override
    public void addUnit(AIUnit au) {
        aiunits.put(au.getUnit().getUnitId(), au);
        banshees.addUnit(au);
        bansheeDPS += au.getDPS();
        au.setLandWhenIdle(false);
        au.setAutoRepair(false);

        troopIdle(au);
    }

    @Override
    public void removeUnit(AIUnit u) {
        aiunits.remove(u.getUnit().getUnitId());
        if (banshees.getUnits().contains(u)) {
            banshees.removeUnit(u, new DevNullHandler(command, clbk));
            bansheeDPS -= u.getDPS();
        }
        repairing.remove(u);
    }

    @Override
    public void troopIdle(AIUnit u) {
        u.wait(command.getCurrentFrame() + 30);

    }

    @Override
    public void troopIdle(AISquad s) {
        s.wait(command.getCurrentFrame() + 30);
    }

    @Override
    public void abortedTask(Task t) {

        if (t instanceof MoveTask) {
            command.mark(((MoveTask) t).getLastExecutingUnit().getPos(), "failed movetask, debug");
        }
    }

    @Override
    public void finishedTask(Task t) {
    }

    @Override
    public void reportSpam() {
        throw new AssertionError("Endless recursion");
    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {
    }

    @Override
    public void unitDestroyed(AIUnit au, Enemy killer) {
        super.unitDestroyed(au, killer);
    }

    @Override
    public boolean retreatForRepairs(AITroop u) {
        return false;
    }

    private Enemy oldtarget = null;

    @Override
    public void update(int frame) {
        if (frame % 50 == 37) {
            for (AIUnit au : banshees.getUnits().toArray(new AIUnit[banshees.getUnits().size()])) {
                if (au.getUnit().getHealth() < 0.66 * au.getDef().getHealth()) {
                    repairing.add(au);
                    banshees.removeUnit(au, this);
                    bansheeDPS -= au.getDPS();
                }
            }
            for (AIUnit au : repairing.toArray(new AIUnit[repairing.size()])) {
                if (au.getUnit().getHealth() > 0.99 * au.getDef().getHealth()) {
                    banshees.addUnit(au);
                    repairing.remove(au);
                    bansheeDPS += au.getDPS();
                    command.mark(au.getPos(), "Banshee repaired");
                }
            }
            if (!repairing.isEmpty()) {
                for (AIUnit au : repairing) {

                    Area bestPos = au.getArea().getNearestArea(command.areaManager.SAFE);
                    float dist = Float.MAX_VALUE;
                    for (AIUnit n : command.getNanoHandler().nanos) {
                        if (n.distanceTo(au.getPos()) < dist) {
                            dist = n.distanceTo(au.getPos());
                            bestPos = n.getArea();
                        }
                    }
                    au.assignTask(new MoveTask(bestPos.getPos(), command.getCurrentFrame() + 60, this, command.pathfinder.AVOID_ANTIAIR, command));
                }
            }
            if (!banshees.getUnits().isEmpty()) {

                Enemy target = null;
                float best = -1e10f;
                for (Enemy e : command.getEnemyUnits(false)) {

                    Area enemyArea = command.areaManager.getArea(e.getPos());
                    if (enemyArea.getEnemyAADPS() <= 0.1) {
                        enemyArea.updateAADPS();
                    }
                    if (enemyArea.getZone() == Zone.hostile && enemyArea.distanceToFront() > 800) {
                        continue;
                    }
                    if (enemyArea.getEnemyAADPS() + (enemyArea.getZone() == Zone.hostile ? 120 : -10) > 10 * banshees.getUnits().size() * banshees.getUnits().size()) {
                        continue;
                    }
                    float score = e.getMetalCost() / e.getHealth() * 1000 / (500 + 3 * banshees.distanceTo(e.getPos())) * 100 / (100 + enemyArea.getEnemyAADPS()) * 200 / (e.getDPS() + 200);
                    float nearbyAlly = 0;
                    float nearbyNoFighter = 0;
                    for (AIUnit au : command.getUnitsIn(e.getPos(), 800)) {
                        if (au.getDef().isAbleToAttack()) {
                            nearbyAlly += au.getMetalCost();
                        } else {
                            nearbyNoFighter += au.getMetalCost();
                        }
                    }
                    if (nearbyAlly < 150 && nearbyNoFighter > 50) {
                        score *= 3;
                    }
                    if (e.getDef().isAbleToCloak() && e.getMetalCost() > 200 && !e.isAntiAir() && enemyArea.getZone() != Zone.hostile) {
                        score *= 5;
                    }
                    if (command.getCommandDelay() > 60 && !e.isVisible()) {
                        score /= 10;
                    }
                    if (e.getDef().getSpeed() > 0.9 * command.getCallback().getUnitDefByName("corhurc2").getSpeed()) {
                        score /= 4;
                    }
                    if (e.getDef().getName().equals("armsolar")) {
                        score /= 4; //compensate for armor
                    }
                    if (enemyArea.getZone() == Zone.own) {
                        score *= 3;
                    }
                    if (e.equals(oldtarget)) {
                        score *= 1.2;
                    }
                    if (score > best) {
                        target = e;
                        best = score;
                    }
                }
                if (target != null) {
                    oldtarget = target;
                    command.debug("Banshees going for " + target.getDef().getHumanName());
                    if (banshees.distanceTo(target.getPos()) > 1600) {
                        banshees.assignTask(new MoveTask(target.getPos(), command.getCurrentFrame() + 60, this, command.pathfinder.AVOID_ANTIAIR, command));
                    } else {
                        banshees.attack(target, command.getCurrentFrame() + 60);
                    }
                } else {
                    oldtarget = null;
                    banshees.assignTask(new MoveTask(banshees.getArea().getNearestArea(command.getCurrentFrame() < 30 * 60 * 5 ? command.areaManager.FRIENDLY : command.areaManager.SAFE).getPos(),
                            command.getCurrentFrame() + 60, this, command.pathfinder.AVOID_ANTIAIR, command));
                    command.debug("no target for banshees");
                }
            }

        }
    }

    @Override
    public void enemyDiscovered(Enemy e) {
    }

    @Override
    public void enemyEnterLOS(Enemy e) {
    }

    @Override
    public void unitFinished(AIUnit u) {
    }
}
