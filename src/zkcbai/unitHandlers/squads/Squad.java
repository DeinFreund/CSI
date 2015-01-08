/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.squads;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import zkcbai.Command;
import zkcbai.unitHandlers.FighterHandler;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.utility.Point;
import zkcbai.utility.SmallestEnclosingCircle;

/**
 *
 * @author User
 */
public abstract class Squad {
    //not exactly a Task but really close

    Set<AIUnit> units = new HashSet();
    OOAICallback clbk;
    Command command;
    FighterHandler fighterHandler;

    public Squad(FighterHandler fighterHandler, Command command, OOAICallback callback) {
        this.command = command;
        this.clbk = callback;
        this.fighterHandler = fighterHandler;
    }

    public AIFloat3 getPos() {
        List<Point> positions = new ArrayList();
        int c = 0;
        for (AIUnit au : units) {
            positions.add(new Point(au.getPos().x, au.getPos().z));
        }
        Point p = SmallestEnclosingCircle.makeCircle(positions).c;
        AIFloat3 pos = new AIFloat3((float) p.x, 0, (float) p.y);
        pos.y = clbk.getMap().getElevationAt(pos.x, pos.z);
        return pos;
    }

    /**
     *
     * @param u
     * @return time to unite with aiUnit u in frames(is incorrect)
     */
    public float timeTo(AIUnit u) {
        return u.distanceTo(getPos()) / u.getUnit().getMaxSpeed();
    }

    public float distanceTo(AIFloat3 trg) {
        AIFloat3 pos = new AIFloat3(getPos());
        pos.sub(trg);
        return pos.length();
    }

    public void addUnit(AIUnit u) {
        units.add(u);
        unitIdle(u);
    }

    public Collection<AIUnit> getUnits(){
        return new ArrayList(units);
    }
    
    public void removeUnit(AIUnit u) {
        units.remove(u);
        if (units.isEmpty()) {
            fighterHandler.squadDestroyed(this);
        }
    }

    public int size() {
        return units.size();
    }

    public float getMetalCost() {
        float res = 0;
        for (AIUnit u : units) {
            res += u.getUnit().getDef().getCost(command.metal);
        }
        return res;
    }

    public Set<AIUnit> disband() {
        Set<AIUnit> res = units;
        units = new HashSet();
        fighterHandler.squadDestroyed(this);
        return res;

    }

    public abstract void unitIdle(AIUnit u);

}
