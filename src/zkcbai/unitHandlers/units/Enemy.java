/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.units;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import com.springrts.ai.oo.clb.UnitDef;
import com.springrts.ai.oo.clb.WeaponMount;
import zkcbai.Command;
import zkcbai.helpers.AreaChecker;
import zkcbai.helpers.Pathfinder;
import zkcbai.helpers.Pathfinder.MovementType;
import zkcbai.helpers.ZoneManager;
import zkcbai.helpers.ZoneManager.Area;

/**
 *
 * @author User
 */
public class Enemy {

    protected static final float regen = 0.01f;
    protected static int idCounter = 0;
    protected Unit unit;
    protected final int id;
    protected int unitId;
    protected final OOAICallback clbk;
    protected final Command command;
    protected UnitDef unitDef;
    protected boolean neverSeen = true;
    protected float maxVelocity = 0.5f;
    protected AIFloat3 lastPos;
    protected AIFloat3 lastPosPossible;
    protected boolean alive = true;
    protected float maxRange = 0;
    protected boolean isBuilding;
    protected int lastSeen = 0;
    protected float health = 0;
    protected AIFloat3 lastAccPos;
    protected int lastAccPosTime;
    protected boolean inLOS = false;

    public Enemy(Unit u, Command cmd, OOAICallback clbk) {
        this(u.getPos(), cmd, clbk);
        setUnitId(u.getUnitId());
        setUnit(u);
    }

    protected Enemy(AIFloat3 pos, Command cmd, OOAICallback clbk) {
        id = idCounter--;
        unitId = id;
        this.clbk = clbk;
        command = cmd;
        lastPos = lastPosPossible = pos;
        isBuilding = false;
        lastSeen = cmd.getCurrentFrame();
        getDef();

    }

    private int deadpollFrame = -1;
    private int destroyFrame = -1;

    private void polledDead() {
        if (deadpollFrame == command.getCurrentFrame() && deadpollFrame != destroyFrame) {
            destroyed();
            command.debug("Dead polled too often: destroying enemy " + hashCode());
            command.enemyDestroyed(this, null);
            try {
                throw new RuntimeException("Polled dead enemy " + hashCode());
            } catch (Exception ex) {
                command.debug("", ex);
            }
        }
        deadpollFrame = command.getCurrentFrame();
    }

    public MovementType getMovementType() {
        if (getDef().isAbleToFly()) {
            return Pathfinder.MovementType.air;
        }
        return Pathfinder.MovementType.getMovementType(getDef());
    }

    /**
     * only use when you can't use a UnitDestroyedListener instead
     *
     * @return
     */
    public boolean isAlive() {
        if (alive && isVisible(true) && unit.getHealth() < 0.01f ) {
            command.mark(unit.getPos(), "missed death event " + hashCode());
            alive = false;
            command.enemyDestroyed(this, null);
        }
        return alive;
    }

    public AIFloat3 getLastAccuratePos() {
        if (unit != null && unit.getHealth() > 0) {
            lastAccPos = unit.getPos();
            lastAccPosTime = command.getCurrentFrame();
        }
        return lastAccPos;
    }

    public int getLastAccuratePosTime() {
        return lastAccPosTime;
    }

    public AIFloat3 getPos() {
        if (!isAlive()) {
            polledDead();
        }
        if (isBuilding() && !neverSeen) {
            return lastPos;
        }
        if (unit != null && unit.getPos().length() > 0) {
            lastPos = unit.getPos();
            return new AIFloat3(lastPos);
        }
        if (command.getCurrentFrame() - lastUpdate > 30) {
            update(command.getCurrentFrame());
        }
        return new AIFloat3(lastPosPossible);
    }

    public AIFloat3 getVel() {
        if (isVisible() && getUnit() != null) {
            return new AIFloat3(getUnit().getVel());
        }
        return new AIFloat3();
    }

    public UnitDef getDef() {
        if (!isAlive()) {
            polledDead();
        }
        if (unit != null && unit.getDef() != null) {
            return unit.getDef();
        }
        if (unitDef == null) {
            identify();
        }
        return unitDef;
    }

    protected void setUnitId(int id) {
        this.unitId = id;
        //command.debug("Set unit id to " + id);
    }

    public float getHealth() {
        if (!isAlive()) {
            polledDead();
        }
        if (isVisible(true)){
            health = unit.getHealth();
        }
        return health;
    }

    public float getRelativeHealth() {
        if (!isAlive()) {
            polledDead();
        }
        return health / getDef().getHealth();
    }

    /**
     * wrong damage for carriers/wolverine/puppy/tacnuke/felon/scorcher/fire
     *
     * @return real damage DPS for units with real damage and magic damage DPS
     * for units with exclusively magic effects
     */
    public float getDPS() {
        if (!isAlive()) {
            polledDead();
        }
        float dps = 0;
        for (WeaponMount wm : getDef().getWeaponMounts()) {
            if (wm.getWeaponDef().getName().toLowerCase().contains("fake")) {
                continue;
            }
            if (wm.getWeaponDef().getName().toLowerCase().contains("noweapon")) {
                continue;
            }
            float maxf = 0;
            for (int i = 1; i < wm.getWeaponDef().getDamage().getTypes().size(); i++) {
                maxf = Math.max(wm.getWeaponDef().getDamage().getTypes().get(i), maxf);
            } //You are entering a land of magic, ask Sprung for directions
            dps += maxf / wm.getWeaponDef().getReload();
        }
        if (isCommander()) {
            return 300;
        }
        return dps;
    }

    protected void setUnit(Unit unit) {
        this.unit = unit;
    }

    public Unit getUnit() {
        if (!isAlive()) {
            polledDead();
        }
        return unit;
    }

    public boolean isCommander() {
        return getDef().getCustomParams().containsKey("commtype");
    }

    public int getUnitId() {
        if (!isAlive()) {
            polledDead();
        }
        return unitId;
    }

    public float getMaxRange() {
        if (!isAlive()) {
            polledDead();
        }
        return maxRange;
    }

    public float distanceTo(AIFloat3 trg) {
        if (!isAlive()) {
            polledDead();
        }
        AIFloat3 pos = new AIFloat3(getPos());
        pos.sub(trg);
        pos.y = 0;
        return pos.length();
    }

    public boolean shouldBeVisible(AIFloat3 pos) {
        if (!isAlive()) {
            polledDead();
        }
        if (getDef().getCloakCost() <= 0 || getDef().isAbleToRepair() || timeSinceLastSeen() > 700 || getDef().isAbleToFly()) {
            return command.radarManager.isInRadar(pos) || command.losManager.isInLos(pos);
        } else {
            return !clbk.getFriendlyUnitsIn(pos, Math.max(150, getDef().getDecloakDistance())).isEmpty();
        }
    }

    public boolean isVisible() {
        return isVisible(false);
    }

    public boolean isVisible(boolean inLos) {
        if (unit == null) {
            return false;
        }
        return (!inLos && unit.getPos().length() > 0) || inLOS;
    }

    private final AreaChecker invisible = new AreaChecker() {

        @Override
        public boolean checkArea(ZoneManager.Area a) {
            if (getDef().getCloakCost() <= 0 || getDef().isAbleToRepair() || timeSinceLastSeen() > 700 || getDef().isAbleToFly()) {
                return a.isVisible() || command.getCurrentFrame() - a.getLastVisible() < 30 * 60 * 3;
            } else {
                return !clbk.getFriendlyUnitsIn(a.getPos(), Math.max(a.getWidth(), a.getHeight()) / 2 + getDef().getDecloakDistance()).isEmpty();
            }
        }

    };

    /**
     *
     * @return time since last in radar/los in frames
     */
    public int timeSinceLastSeen() {
        if (!isAlive()) {
            polledDead();
        }
        if (isBuilding) {
            return 0;
        }
        return command.getCurrentFrame() - lastSeen;
    }

    public boolean isTimedOut() {
        if (!isAlive()) {
            polledDead();
        }
        return timeSinceLastSeen() > 1500 * 100 / Math.max(getDef().getSpeed(), 10);
    }

    private int lastUpdate = 0;

    public void update(int frame) {
        if (!isAlive()) {
            return;
        }
        lastUpdate = frame;
        health = Math.min(getDef().getHealth(), health + regen * getDef().getHealth());
        if (unit != null && unit.getHealth() > 0) {
            //in LOS
            health = unit.getHealth();
            lastAccPos = unit.getPos();
            lastAccPosTime = frame;
        }
        if (unit != null && unit.getPos().length() > 0) {
            //in Radar
            lastPos = lastPosPossible = unit.getPos();
            lastSeen = command.getCurrentFrame();
        } else if (shouldBeVisible(lastPosPossible) && command.getCurrentFrame() - lastSeen > 10) {
            //command.debug("new pos");
            if (isBuilding) {
                if (this instanceof FakeEnemy) {
                    command.removeFakeEnemy((FakeEnemy) this);
                } else {
                    command.unitDestroyed(unit, null);
                }
            } else {
                Area npos = command.areaManager.getArea(lastPos).getNearestArea(invisible, getMovementType());
                if (npos != null) {
                    lastPosPossible = npos.getPos();
                }
            }
        }else{
            /*if (getDef().getHumanName().equalsIgnoreCase("blastwing")){
                command.mark(lastPosPossible, "invisible blastwing");
            }*/
        }
        if (neverSeen && unit != null && unit.getVel().length() > maxVelocity) {
            identify();
        }
        //command.mark(getPos(), getPos().toString());
    }

    public void enterLOS() {
        inLOS = true;
        if (!isAlive()) {
            polledDead();
        }
        
        if (neverSeen) {
            health = unit.getHealth();
            unitDef = unit.getDef();
            maxRange = unit.getMaxRange();
            if (unitDef.getName().equals("cormex")) {
                command.areaManager.getNearestMex(getPos()).setEnemyMex(this);
            }
            isBuilding = unit.getDef().getSpeed() <= 0;
        }
        neverSeen = false;
        lastSeen = command.getCurrentFrame();
        update(command.getCurrentFrame());
    }

    public float getMetalCost() {
        if (!isAlive()) {
            polledDead();
        }
        return getDef().getCost(command.metal);
    }

    public boolean isBuilding() {
        if (!isAlive()) {
            polledDead();
        }
        return isBuilding;
    }

    public boolean isIdentifiedByRadar() {
        if (!isAlive()) {
            polledDead();
        }
        return neverSeen && getDef().getSpeed() > 0.5;
    }

    public void identify() {
        if (!isAlive()) {
            polledDead();
        }
        if (!neverSeen) {
            return;
        }
        if (unit != null) {
            maxVelocity = Math.max(unit.getVel().length(), maxVelocity);
            if (command.getEnemyUnitDefSpeedMap().ceilingEntry(unit.getVel().length() - 1e-6f) != null) {
                unitDef = command.getEnemyUnitDefSpeedMap().ceilingEntry(unit.getVel().length() - 1e-6f).getValue();
            }
        }
        if (unitDef == null) {
            unitDef = clbk.getUnitDefByName("armpw");
        }
        maxRange = 0;
        for (WeaponMount wm : unitDef.getWeaponMounts()) {
            maxRange = Math.max(wm.getWeaponDef().getRange(), maxRange);
        }
        //command.debug(unitDef.getHumanName() + " identified by Radar");
        health = unitDef.getHealth();
    }

    public void enterRadar() {
        lastSeen = command.getCurrentFrame();
        update(command.getCurrentFrame());
    }

    public void leaveLOS() {
        inLOS = false;
        lastSeen = command.getCurrentFrame();
        update(command.getCurrentFrame());
    }

    public void leaveRadar() {
        lastSeen = command.getCurrentFrame();
        update(command.getCurrentFrame());
    }

    public void destroyed() {
        //command.mark(getPos(), "dead");
        alive = false;
        destroyFrame = command.getCurrentFrame();
        command.debug("Enemy " + hashCode() + "(" + getDef().getHumanName() + ") has been destroyed");
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Enemy) {
            return equals((Enemy) o);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return id;
    }

    public boolean equals(Enemy e) {
        if (e == null) {
            return false;
        }
        return hashCode() == e.hashCode();
    }
}
