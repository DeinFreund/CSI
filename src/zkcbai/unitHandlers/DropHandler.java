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
import com.springrts.ai.oo.clb.WeaponDef;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import zkcbai.Command;
import zkcbai.UpdateListener;
import zkcbai.helpers.ZoneManager;
import zkcbai.unitHandlers.FactoryHandler.Factory;
import zkcbai.unitHandlers.units.AISquad;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.AttackTask;
import zkcbai.unitHandlers.units.tasks.DropTask;
import zkcbai.unitHandlers.units.tasks.LoadUnitTask;
import zkcbai.unitHandlers.units.tasks.MoveTask;
import zkcbai.unitHandlers.units.tasks.Task;
import zkcbai.unitHandlers.units.tasks.WaitTask;

/**
 *
 * @author User
 */
public class DropHandler extends UnitHandler implements UpdateListener {

    public final UnitDef ROACH = clbk.getUnitDefByName("corroach");
    public final UnitDef SKUTTLE = clbk.getUnitDefByName("corsktl");
    public final UnitDef VALK = clbk.getUnitDefByName("corvalk");
    public final UnitDef VINDI = clbk.getUnitDefByName("corbtrans");
    public final UnitDef GNAT = clbk.getUnitDefByName("bladew");
    public final UnitDef LICHO = clbk.getUnitDefByName("armcybr");

    private Map<AIUnit, AIUnit> loadedTransports = new HashMap();
    private Set<AIUnit> emptyTransports = new HashSet();
    private Set<AIUnit> roaches = new HashSet();
    private Set<AIUnit> skuttles = new HashSet();
    private Set<AIUnit> lichos = new HashSet();
    private Set<AIUnit> gnats = new HashSet();

    public DropHandler(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        cmd.addUpdateListener(this);
    }

    @Override
    public AIUnit addUnit(Unit u) {
        AIUnit au = new AIUnit(u, this);
        aiunits.put(u.getUnitId(), au);

        if (u.getDef().equals(VALK) || u.getDef().equals(VINDI)) {
            emptyTransports.add(au);
        }
        if (u.getDef().equals(LICHO)) {
            lichos.add(au);
            au.setLandWhenIdle(false);
        }
        if (u.getDef().equals(ROACH)) {
            buildingRoach = false;
            roaches.add(au);
        }
        if (u.getDef().equals(VALK)) {
            buildingValk = false;
        }
        if (u.getDef().equals(SKUTTLE)) {
            buildingSkuttle = false;
            skuttles.add(au);
        }
        if (u.getDef().equals(GNAT)) {
            gnats.add(au);
        }
        troopIdle(au);
        return au;
    }

    @Override
    public void removeUnit(AIUnit u) {
        aiunits.remove(u.getUnit().getUnitId());
        loadedTransports.remove(u);
        emptyTransports.remove(u);
        roaches.remove(u);
        skuttles.remove(u);
        gnats.remove(u);
        lichos.remove(u);
        Map.Entry<AIUnit, AIUnit> toremove = null;
        for (Map.Entry<AIUnit, AIUnit> entry : loadedTransports.entrySet()) {
            if (entry.getValue().equals(u)) {
                toremove = entry;
            }
        }
        if (toremove != null) {
            loadedTransports.remove(toremove.getKey());
            emptyTransports.add(toremove.getKey());
        }
    }

    @Override
    public void troopIdle(AIUnit u) {
        if (emptyTransports.contains(u)) {
            AIUnit payload = null;
            for (AIUnit au : roaches) {
                if (payload == null || au.distanceTo(u.getPos()) < payload.distanceTo(u.getPos())) {
                    payload = au;
                }
            }
            for (AIUnit au : skuttles) {
                if (payload == null || au.distanceTo(u.getPos()) < payload.distanceTo(u.getPos())) {
                    payload = au;
                }
            }
            if (payload != null) {
                u.assignTask(new LoadUnitTask(payload, this, command));
                skuttles.remove(payload);
                roaches.remove(payload);
                return;
            } else {
                u.assignTask(new MoveTask(u.getArea().getNearestArea(command.areaManager.SAFE).getPos(), command.getCurrentFrame() + 100, this, command));
            }
        }
        if (loadedTransports.containsKey(u)) {

            Enemy vip = null;
            if (loadedTransports.get(u).getDef().equals(SKUTTLE)) {
                for (Enemy e : command.getEnemyUnits(true)) {
                    if (command.distance2D(e.getPos(), e.getLastAccuratePos()) > 500 || e.isAbleToFly()) {
                        continue;
                    }
                    if (command.areaManager.getArea(e.getPos()).getEnemyAADPS() > 150) {
                        continue;
                    }
                    if (e.getMetalCost() < 800 || command.areaManager.getArea(e.getPos()).getEnemyAADPS() > 150) {
                        continue;
                    }
                    if (vip == null || e.getLastAccuratePosTime() > vip.getLastAccuratePosTime()) {
                        if (e.getArea().getNearestArea(command.areaManager.FRIENDLY).distanceTo(e.getPos()) < 1300 || command.getCurrentFrame() < 30 * 60 * 10) {
                            vip = e;
                        }
                    }
                }
                if (vip == null || command.getCurrentFrame() - vip.getLastAccuratePosTime() > 300) {
                    for (Enemy e : command.getEnemyUnits(true)) {
                        if (!e.isBuilding() || e.getMetalCost() < 600 || command.areaManager.getArea(e.getPos()).getEnemyAADPS() > 150) {
                            continue;
                        }
                        if (vip == null || command.areaManager.getArea(e.getPos()).getEnemyAADPS() < command.areaManager.getArea(vip.getPos()).getEnemyAADPS()) {
                            if (e.getArea().getNearestArea(command.areaManager.FRIENDLY).distanceTo(e.getPos()) < 1300 || command.getCurrentFrame() < 30 * 60 * 10) {
                                vip = e;
                            }
                        }
                    }
                }
                if (vip == null) {
                    command.debug("Couldn't find target for skuttledrop");
                }

                if (vip != null && command.getCurrentFrame() - vip.getLastAccuratePosTime() > 300) {
                    command.getAvengerHandler().requestScout(command.areaManager.getArea(vip.getPos()));
                } else if (vip != null/* && !command.getAvengerHandler().getUnits().isEmpty()*/ && !gnats.isEmpty()) {
                    u.assignTask(new DropTask(vip, this, command));
                    return;
                }
            } else if (loadedTransports.get(u).getDef().equals(ROACH)) {
                vip = getAOETarget(ROACH.getDeathExplosion().getAreaOfEffect(), 0.92f * ROACH.getDeathExplosion().getDamage().getTypes().get(1), ROACH.getDeathExplosion().getEdgeEffectiveness(), 0.92f * (ROACH.getCost(command.metal) + VALK.getCost(command.metal)), 150, new AIFloat3(), Float.MAX_VALUE);

                if (vip != null && command.getCommandDelay() < 30) {
                    if (u.distanceTo(vip.getPos()) > 2500) {
                        u.assignTask(new MoveTask(vip.getPos(), command.getCurrentFrame() + 60, this, command.pathfinder.AVOID_ANTIAIR, command));
                    } else {
                        u.assignTask(new DropTask(vip, this, command));
                    }
                    command.mark(vip.getPos(), "nice cluster");
                    return;
                } else {
                    AIFloat3 safepos = u.getArea().getNearestArea(command.areaManager.SAFE).getPos();
                    if (u.distanceTo(safepos) > 300) {
                        u.assignTask(new MoveTask(safepos, command.getCurrentFrame() + 60, this, command.pathfinder.AVOID_ANTIAIR, command));
                    } else {
                        u.assignTask(new WaitTask(command.getCurrentFrame() + 100, this));
                    }
                    return;
                }
            }
        } else if (lichos.contains(u)) {
            WeaponDef wd = LICHO.getWeaponMounts().get(0).getWeaponDef();
            Enemy vip = getAOETarget(wd.getAreaOfEffect(), wd.getDamage().getTypes().get(1), wd.getEdgeEffectiveness(), 0f, 1000, u.getPos(), u.getArea().getZone() == ZoneManager.Zone.hostile ? 1000 : Float.MAX_VALUE);

            boolean isRepairing = false;
            for (AIUnit au : command.getUnitsIn(u.getPos(), 100)) {
                if (au.getDef().isAbleToAssist() || au.getDef().getBuildOptions().size() > 5) {
                    isRepairing = u.getHealth() / u.getDef().getHealth() < 0.96;
                }
            }
            if (vip == null) {
                command.debug("No target for licho");
            }
            if (vip != null && command.getCommandDelay() < 30 && u.getUnit().getRulesParamFloat("noammo", 0) < 1f && !isRepairing) {
                if (u.distanceTo(vip.getPos()) > 1500) {
                    u.assignTask(new MoveTask(vip.getPos(), command.getCurrentFrame() + 60, this, command.pathfinder.AVOID_ANTIAIR, command));
                } else {
                    u.attack(vip, command.getCurrentFrame() + 15);
                }
                command.mark(vip.getPos(), "licho targeting " + vip.getDef().getHumanName());
                return;
            } else if (u.getArea().getZone() != ZoneManager.Zone.own) {
                AIFloat3 safepos = u.getArea().getNearestArea(command.areaManager.SAFE).getPos();
                if (u.distanceTo(safepos) > 300) {
                    u.assignTask(new MoveTask(safepos, command.getCurrentFrame() + 60, this, command.pathfinder.AVOID_ANTIAIR, command));
                    command.debug("licho retreating");
                    return;
                }
            }
            u.assignTask(new WaitTask(command.getCurrentFrame() + 300, this));
            if (u.distanceTo(command.getStartPos()) > 1500) {
                u.moveTo(command.getStartPos(), command.getCurrentFrame() + 100);
                command.debug("licho going home");
            } else {
                u.wait(command.getCurrentFrame() + 100);
                command.debug("licho reloading");
            }
            return;
        }

        u.wait(command.getCurrentFrame() + 30);
    }

    protected Enemy getAOETarget(final float blastradius, final float damage, final float edgeEffectiveness, final float minValue, final float maxAA, final AIFloat3 pos, final float searchRange) {
        final float falloff = (1f - edgeEffectiveness) / blastradius;
        float bestMetal = 0;
        Enemy vip = null;
        for (Enemy e : command.getEnemyUnits(true)) {
            if (e.timeSinceLastSeen() > 30 * 10 || e.getDef().isAbleToFly() || Command.distance2D(e.getPos(), e.getLastPos()) > 200
                    || e.distanceTo(pos) > searchRange || (e.getDef().isAbleToCloak() && ( e.getMetalCost() < 1000 || e.timeSinceLastSeen() > 30 * 3))) {
                continue;
            }
            float metalKilled = 0;
            for (Enemy near : command.getEnemyUnitsIn(e.getPos(), blastradius)) {

                if (near.timeSinceLastSeen() > 30 * 5 || near.getDef().isAbleToFly()) {
                    continue;
                }
                if (near.getHealth() > damage * (1 - near.distanceTo(e.getPos()) * falloff)) {
                    metalKilled += 0.33 * near.getMetalCost() * damage * (1 - near.distanceTo(e.getPos()) * falloff) / near.getDef().getHealth();
                } else {
                    metalKilled += near.getMetalCost();
                }
            }
            if (command.areaManager.getArea(e.getPos()).getEnemyAADPS() > maxAA
                    || metalKilled < minValue) {
                continue;
            }
            if (metalKilled > bestMetal) {
                if (e.getArea().getNearestArea(command.areaManager.FRIENDLY).distanceTo(e.getPos()) < 900 || command.getCurrentFrame() < 30 * 60 * 8) {
                    vip = e;
                    bestMetal = metalKilled;
                }
            }
        }
        return vip;
    }

    /**
     *
     * @param au
     * @return AIUnit that is inside au or null otherwise
     */
    public AIUnit getPayload(AIUnit au) {
        return loadedTransports.get(au);
    }

    @Override
    public void troopIdle(AISquad s) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void abortedTask(Task t) {

        if (t instanceof LoadUnitTask) {
            LoadUnitTask lt = (LoadUnitTask) t;
            loadedTransports.remove(lt.getWorkers().iterator().next());
            emptyTransports.add(lt.getWorkers().iterator().next());
        }
    }

    @Override
    public void finishedTask(Task t) {

        if (t instanceof LoadUnitTask) {
            LoadUnitTask lt = (LoadUnitTask) t;
            loadedTransports.put(lt.getWorkers().iterator().next(), lt.getTarget());
            emptyTransports.remove(lt.getWorkers().iterator().next());
        }
        if (t instanceof DropTask) {
            DropTask dt = (DropTask) t;
            loadedTransports.remove(dt.getWorkers().iterator().next());
            emptyTransports.add(dt.getWorkers().iterator().next());
        }
    }

    @Override
    public void reportSpam() {
        throw new AssertionError("Endless recursion");
    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {

    }

    public Set<AIUnit> getGnats() {
        return gnats;
    }

    @Override
    public boolean retreatForRepairs(AITroop u) {
        return false;
    }

    private boolean buildingRoach = false;
    private boolean buildingValk = false;
    private boolean buildingSkuttle = false;

    @Override
    public void update(int frame) {
        if (frame % 31 == 3) {
            command.debug("checking lichos");
            for (AIUnit au : lichos) {
                au.checkIdle();
            }
        }
        Factory gs = null;
        Factory shield = null;
        Factory jj = null;
        for (Factory f : command.getFactoryHandler().getFacs()) {
            if (f.unit.getDef().getName().equalsIgnoreCase("factorygunship")) {
                gs = f;
            }
            if (f.unit.getDef().getName().equalsIgnoreCase("factoryshield")) {
                shield = f;
            }
            if (f.unit.getDef().getName().equalsIgnoreCase("factoryjump")) {
                jj = f;
            }
        }
        if (gs != null && (command.getFactoryHandler().getFacs().size() >= 4 || command.getCurrentFrame() > 30 * 60 * 4)) {

            if (!buildingValk && emptyTransports.size() + loadedTransports.size() < roaches.size() + skuttles.size() + (buildingSkuttle ? 1 : 0) + (buildingRoach ? 1 : 0)) {
                gs.queueUnit(VALK);
                buildingValk = true;
            }
            if (jj != null && !buildingSkuttle && skuttles.size() < 1) {
                jj.queueUnit(SKUTTLE);
                gs.queueUnit(GNAT);
                buildingSkuttle = true;
            }
            if (shield != null && !buildingRoach && roaches.size() < 1) {
                shield.queueUnit(ROACH);
                buildingRoach = true;
            }
        }
    }

}
