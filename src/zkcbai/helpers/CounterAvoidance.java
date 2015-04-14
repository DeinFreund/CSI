/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.helpers;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.UnitDef;
import zkcbai.Command;
import zkcbai.unitHandlers.units.Enemy;

/**
 *
 * @author User
 */
public class CounterAvoidance implements CostSupplier {

        UnitDef def;
        Command command;

        public CounterAvoidance(UnitDef ud, Command command) {
            this.def = ud;
            this.command = command;
        }

        @Override
        public float getCost(float slope, float maxSlope, AIFloat3 pos) {
            float danger = 0;
            for (Enemy e : command.getEnemyUnits(false)) {
                if (e.distanceTo(pos) < e.getMaxRange() * 1.4 && command.killCounter.getEfficiency(e.getDef(), def) > 3) {
                    danger += e.getMetalCost();
                }
            }
            return 10 * (slope / maxSlope + ((slope > maxSlope) ? (1000) : (0))) + 1 + danger / 10 + command.defenseManager.getGeneralDanger(pos)/100;
        }

    }