/**
 * createVariableCollection
 *
 * Creates a new Figma variable collection with the specified name and modes.
 * If `modeNames` has more than one entry, the first mode is renamed from
 * Figma's default "Mode 1" to the first name, and additional modes are added.
 *
 * @param {string} name - The display name of the collection (e.g. "Color", "Spacing").
 * @param {string[]} modeNames - Ordered list of mode names (e.g. ["Light", "Dark"] or ["Value"]).
 * @returns {Promise<{
 *   collection: VariableCollection,
 *   modeIds: Record<string, string>
 * }>}
 *   `modeIds` maps each mode name to its modeId string.
 */
async function createVariableCollection(name, modeNames) {
  if (!modeNames || modeNames.length === 0) {
    throw new Error('createVariableCollection: modeNames must have at least one entry.')
  }

  // Create the collection — Figma always creates it with one mode named "Mode 1".
  const collection = figma.variables.createVariableCollection(name)

  // modeIds accumulator
  const modeIds = {}

  // Rename the default first mode
  const defaultMode = collection.modes[0]
  collection.renameMode(defaultMode.modeId, modeNames[0])
  modeIds[modeNames[0]] = defaultMode.modeId

  // Add additional modes
  for (let i = 1; i < modeNames.length; i++) {
    const newModeId = collection.addMode(modeNames[i])
    modeIds[modeNames[i]] = newModeId
  }

  return { collection, modeIds }
}

const createdNodeIds = [], mutatedNodeIds = [];
const page = figma.currentPage;
if (page.children.length !== 0) throw new Error("Expected empty reference page; inspect before retrying.");
page.name = "Marca clásica"; mutatedNodeIds.push(page.id);
await figma.loadFontAsync({family:"Inter",style:"Regular"});
await figma.loadFontAsync({family:"Inter",style:"Semi Bold"});
const {collection, modeIds} = await createVariableCollection("Qetara · Marca", ["Clásico"]);
const hex = value => ({r:parseInt(value.slice(1,3),16)/255,g:parseInt(value.slice(3,5),16)/255,b:parseInt(value.slice(5,7),16)/255});
const colors = {};
for (const [name,value,syntax] of [["color/marca/fondo","#FFF7ED","R.drawable.ic_launcher_background"],["color/marca/simbolo","#102A43","R.drawable.ic_launcher_foreground"]]) {
 const v=figma.variables.createVariable(name,collection,"COLOR");
 v.scopes=["FRAME_FILL","SHAPE_FILL"]; v.setValueForMode(modeIds["Clásico"],{...hex(value),a:1});
 v.setVariableCodeSyntax("ANDROID",syntax); colors[name]=v;
}
const bind = (node,v) => {node.fills=[figma.variables.setBoundVariableForPaint({type:"SOLID",color:hex("#000000")},"color",v)];};
const source=figma.createNodeFromSvg("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"108\" height=\"108\" viewBox=\"0 0 108 108\">\n  <rect width=\"108\" height=\"108\" rx=\"24\" fill=\"#FFF7ED\"/>\n  <path fill=\"#102A43\" transform=\"translate(54 54) scale(0.65625) translate(-54 -54)\" d=\"M78.8495,21.6103Q76.4784,21.8948 73.0165,23.2227Q69.5546,24.5505 64.7175,27.5856Q59.8804,30.6206 53.1464,36.0268Q45.6536,42.1918 40.8165,45.4639Q35.9794,48.7361 32.6598,50.0165Q29.3402,51.2969 26.2103,51.2969H24.6928Q25.2619,49.3052 26.0206,45.8907Q26.7794,42.4763 27.5856,38.4454Q28.3918,34.4144 29.1031,30.5258Q29.8144,26.6371 30.1938,23.6969H14.2598Q13.3113,29.8619 11.9361,36.7381Q10.5608,43.6144 9.0433,50.1588Q8.3794,53.3835 8.1897,55.6124Q8,57.8412 8,58.5052Q8,66.0928 12.7423,68.3691Q19.3814,67.4206 24.5505,67.1835Q29.7196,66.9464 31.8062,66.9464Q49.068,66.9464 63.532,71.6887Q77.9959,76.4309 90.0412,86.3897L100,75.3876Q94.5938,69.6021 86.7216,65.0495Q78.8495,60.4969 70.0763,57.699Q61.3031,54.901 52.9567,54.2371V53.8577Q57.0351,52.0557 59.9753,50.301Q62.9155,48.5464 66.4247,46.2701Q69.2701,44.468 71.9258,42.8557Q74.5814,41.2433 76.9526,39.8206Q79.4186,38.3031 81.8845,37.3072Q84.3505,36.3113 86.7216,35.6474Z\"/>\n</svg>\n");
source.name="Qetara · vector original";
const component=figma.createComponentFromNode(source); component.name="Marca/Qetara clásico";
component.description="Fondo marfil #FFF7ED y aleph #102A43. Vector original sin alterar: viewport 108 × 108, escala interna 0.65625. Radio 24 para escritorio; Android aplica su máscara adaptativa.";
component.x=1100; component.y=120;
const shapeNodes=component.findAll(n=>"fills" in n);
for (const n of shapeNodes) {
 const fills=n.fills;
 if (Array.isArray(fills) && fills[0]?.type==="SOLID") {
 const c=fills[0].color;
 bind(n,c.r>0.9 ? colors["color/marca/fondo"] : colors["color/marca/simbolo"]);
 }
}
const frame=(name,direction,width) => {const f=figma.createAutoLayout(direction); f.name=name; f.resize(width,10); f.layoutSizingHorizontal="FIXED"; f.layoutSizingVertical="HUG"; f.itemSpacing=24; f.fills=[]; f.clipsContent=false; return f;};
const text=(parent,value,size=16,bold=false,width=800)=>{const n=figma.createText(); n.fontName={family:"Inter",style:bold?"Semi Bold":"Regular"}; n.fontSize=size; n.lineHeight={unit:"PERCENT",value:145}; n.characters=value; n.fills=[{type:"SOLID",color:hex("#102A43")}]; n.resize(width,20); parent.appendChild(n); n.textAutoResize="HEIGHT"; n.layoutSizingHorizontal="FIXED"; n.layoutSizingVertical="HUG"; return n;};
const review=frame("Qetara · Marca clásica · Referencia aprobada", "VERTICAL", 920);
review.x=120; review.y=120; review.paddingLeft=48; review.paddingRight=48; review.paddingTop=48; review.paddingBottom=48;
review.fills=[{type:"SOLID",color:hex("#F4F6F5")}];
text(review,"Qetara · Marca clásica",32,true,824);
text(review,"Marfil cálido. El símbolo original, con su proporción intacta.",16,false,824);
const row=frame("Marca y colores","HORIZONTAL",824); row.counterAxisAlignItems="CENTER"; row.itemSpacing=40; review.appendChild(row);
const instance=component.createInstance(); row.appendChild(instance); instance.rescale(1.5);
const labels=frame("Paleta de marca","VERTICAL",560); labels.itemSpacing=12; row.appendChild(labels);
text(labels,"Fondo  #FFF7ED",22,true,560); text(labels,"Símbolo  #102A43",22,true,560);
text(labels,"Viewport 108 × 108 · escala interna 0.65625\nSilueta: 1.420204571 : 1",15,false,560);
text(review,"Aplicación",22,true,824);
text(review,"Android: restaurar el fondo marfil del icono y sus recursos de launcher.\nEscritorio: conservar la marca clásica existente.\nLa cabecera, los temas y el splash siguen su comportamiento actual.",16,false,824);
text(review,"Referencia vectorial editable · 5 de octubre de 2026\nEste panel documenta la marca; no es una captura de la aplicación.",13,false,824);
for (const n of [component,...component.findAll(),review,...review.findAll()]) createdNodeIds.push(n.id);
return {createdNodeIds,mutatedNodeIds,pageId:page.id,reviewId:review.id,componentId:component.id,collectionId:collection.id,variables:Object.fromEntries(Object.entries(colors).map(([k,v])=>[k,{id:v.id,scopes:v.scopes,valuesByMode:v.valuesByMode}])),componentBounds:{width:component.width,height:component.height},reviewBounds:{width:review.width,height:review.height}};
