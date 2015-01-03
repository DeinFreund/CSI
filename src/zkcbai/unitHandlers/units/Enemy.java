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
import zkcbai.Command;
import zkcbai.UpdateListener;

/**
 *
 * @author User
 */
public class Enemy implements UpdateListener {

    private Unit unit;
    private int unitId;
    private OOAICallback clbk;
    private Command command;
    private UnitDef unitDef;
    private boolean neverSeen = true;
    private float maxVelocity = 0.5f;
    private AIFloat3 lastPos;
    private boolean alive = true;

    public Enemy(Unit u, Command cmd, OOAICallback clbk) {
        unit = u;
        unitId = u.getUnitId();
        this.clbk = clbk;
        command = cmd;
        unitDef = clbk.getUnitDefByName("corllt");
        cmd.addSingleUpdateListener(this, cmd.getCurrentFrame() + 40);
    }
    
    @Deprecated
    public boolean isAlive(){
        return alive;
    }

    public AIFloat3 getPos() {
        if (unit.getPos().length() > 0) {
            return unit.getPos();
        }
        return lastPos;
    }

    public UnitDef getDef() {
        if (unit.getDef() != null) {
            return unit.getDef();
        }
        return unitDef;
    }

    public Unit getUnit() {
        return unit;
    }

    public float distanceTo(AIFloat3 trg) {
        AIFloat3 pos = new AIFloat3(getPos());
        pos.sub(trg);
        return pos.length();
    }

    @Override
    public void update(int frame) {
        if (unit.getPos().length() > 0) {
            lastPos = unit.getPos();
        } else if (command.radarManager.isInRadar(lastPos) || command.losManager.isInLos(lastPos)) {
            //command.debug("new pos");
            lastPos = command.areaManager.getArea(lastPos).getNearestInvisibleArea().getPos();
        }
        //command.mark(getPos(), getPos().toString());
        command.addSingleUpdateListener(this, frame + 40);
    }

    public void enterLOS() {
        unitDef = unit.getDef();
        if (unitDef.getName().equals("cormex")){
            command.areaManager.getNearestMex(getPos()).setEnemyMex(this);
        }
        neverSeen = false;
    }

    public void identify() {
        if (!neverSeen) return;
        maxVelocity = Math.max(unit.getVel().length(), maxVelocity);
        if (command.getEnemyUnitDefSpeedMap().ceilingEntry(unit.getVel().length()) == null) {
            unitDef = clbk.getUnitDefByName("armpw");
        } else {
            unitDef = command.getEnemyUnitDefSpeedMap().ceilingEntry(unit.getVel().length()).getValue();
        }
        command.debug(unitDef.getHumanName() + " identified by Radar");
    }

    public void enterRadar() {
        if (neverSeen && unit.getVel().length() > maxVelocity) {
            identify();
        }
    }

    public void leaveLOS() {
    }

    public void leaveRadar() {
    }
    
    public void destroyed(){
        alive = false;
    }
    
    public boolean equals(Enemy e){
        return unit.getUnitId() == e.getUnit().getUnitId();
    }
}
