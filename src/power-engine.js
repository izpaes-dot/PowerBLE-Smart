export class PowerEngine {
  constructor() {
    this.cadence = 0;
    this.speedKmh = 0;
    this.grade = 0;
    this.massKg = 120;
    this.lastGps = null;
  }
  setCadence(v){ this.cadence = Number(v)||0; }
  setGps(d){
    this.speedKmh=(Number(d.speedMps)||0)*3.6;
    this.grade=Number(d.grade)||0;
    this.lastGps=d;
  }
  calculate(){
    const v=this.speedKmh/3.6;
    const g=this.grade/100;
    const theta=Math.atan(g);
    const m=this.massKg;
    const rolling=0.005*m*9.80665;
    const aero=0.5*1.225*0.45*v*v;
    const climb=m*9.80665*Math.sin(theta);
    const base=(rolling+aero+climb)*v;
    const cadenceAssist=this.cadence>0 ? Math.max(0,this.cadence-40)*0.15 : 0;
    return Math.max(0, base+cadenceAssist);
  }
}
