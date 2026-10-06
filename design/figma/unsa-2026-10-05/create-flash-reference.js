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

const created=[],mutated=[];
if(figma.root.children.some(p=>p.name==="Flash · Lotes")) throw new Error("Page already exists; inspect before retry.");
const page=figma.createPage(); page.name="Flash · Lotes"; created.push(page.id); await figma.setCurrentPageAsync(page);
const fonts=await figma.listAvailableFontsAsync();
const mono=fonts.find(f=>f.fontName.family==="Roboto Mono"&&f.fontName.style==="Medium")?.fontName;
if(!mono) throw new Error("Need available monospace font before creating UI.");
for(const font of [{family:"Inter",style:"Regular"},{family:"Inter",style:"Semi Bold"},mono])await figma.loadFontAsync(font);
const {collection,modeIds}=await createVariableCollection("Qetara · Flash",["Referencia"]);
const rgb=h=>({r:parseInt(h.slice(1,3),16)/255,g:parseInt(h.slice(3,5),16)/255,b:parseInt(h.slice(5,7),16)/255});
const vars={};
for(const [name,value] of Object.entries({canvas:"#F4F6F5",surface:"#FFFFFF",mobileSurface:"#F1F5F7",ink:"#102A43",muted:"#536672",accent:"#0A6B77",accentSoft:"#DDF1F0",accentInk:"#075460",card:"#E7EFF2"})){
const v=figma.variables.createVariable("color/"+name,collection,"COLOR"); v.scopes=["FRAME_FILL","SHAPE_FILL","TEXT_FILL"];v.setValueForMode(modeIds.Referencia,{...rgb(value),a:1});vars[name]=v;
}
for(const [name,value,scope] of [["gap",16,"GAP"],["padding",24,"GAP"],["radius",16,"CORNER_RADIUS"]]){
const v=figma.variables.createVariable(name,collection,"FLOAT");v.scopes=[scope];v.setValueForMode(modeIds.Referencia,value);vars[name]=v;
}
const paint=(node,key)=>{node.fills=[figma.variables.setBoundVariableForPaint({type:"SOLID",color:{r:0,g:0,b:0}},"color",vars[key])];};
const styles={};for(const [name,size,weight] of [["title",22,"Semi Bold"],["body",14,"Regular"],["label",14,"Semi Bold"],["small",12,"Regular"]]){
const s=figma.createTextStyle();s.name="Qetara/"+name;s.fontName={family:"Inter",style:weight};s.fontSize=size;s.lineHeight={unit:"PERCENT",value:145};styles[name]=s;
}
const box=(name,width,parent,dir="VERTICAL")=>{const n=figma.createAutoLayout(dir);n.name=name;n.resize(width,20);if(parent)parent.appendChild(n);n.layoutSizingHorizontal="FIXED";n.layoutSizingVertical="HUG";n.setBoundVariable("itemSpacing",vars.gap);n.fills=[];n.clipsContent=false;return n;};
const pad=n=>{for(const side of ["paddingTop","paddingBottom","paddingLeft","paddingRight"])n.setBoundVariable(side,vars.padding);};
const rounded=n=>{for(const corner of ["topLeftRadius","topRightRadius","bottomLeftRadius","bottomRightRadius"])n.setBoundVariable(corner,vars.radius);};
const txt=(parent,value,width,style="body",color="ink")=>{const n=figma.createText();n.textStyleId=styles[style].id;n.characters=value;n.resize(width,20);parent.appendChild(n);n.textAutoResize="HEIGHT";n.layoutSizingHorizontal="FIXED";n.layoutSizingVertical="HUG";paint(n,color);return n;};
const board=box("Flash · Confirmar el lote completo",1080,null);board.x=120;board.y=120;board.paddingTop=40;board.paddingBottom=40;board.paddingLeft=40;board.paddingRight=40;paint(board,"canvas");
txt(board,"Flash · Una confirmación para todos los archivos",1000,"title");
txt(board,"Una vez en cada equipo. La aprobación vale sólo para este lote y esta conexión.",1000);
const columns=box("Emisor y receptor",1000,board,"HORIZONTAL");columns.itemSpacing=40;columns.counterAxisAlignItems="MIN";
const makeDialog=(width,outgoing,x)=>{
const holder=box(outgoing?"Escritorio · Emisor":"Android · Receptor",width,columns);
txt(holder,outgoing?"ESCRITORIO · ENVIAR":"ANDROID · RECIBIR",width,"label","muted");
const f=box(outgoing?"Flash/Confirmación/Enviar lote":"Flash/Confirmación/Recibir lote",width,null);f.x=x;f.y=1300;pad(f);rounded(f);paint(f,outgoing?"surface":"mobileSurface");
const inside=width-48;
txt(f,outgoing?"Verifica antes de enviar":"Verifica antes de recibir",inside,"title");
txt(f,"Equipo: Equipo de ejemplo\n192.0.2.20",inside,"body","muted");
const card=box("Archivos de este lote",inside,f);card.paddingLeft=16;card.paddingRight=16;card.paddingTop=16;card.paddingBottom=16;rounded(card);paint(card,"card");
txt(card,"3 archivos · 6.0 MB en total",inside-32,"label");
txt(card,"Notas de reunión.txt · 1.0 MB\nEsquema del proyecto.pdf · 2.0 MB\nImagen de muestra.png · 3.0 MB",inside-32);
txt(f,"Compara estos cuatro grupos en los dos equipos. Deben coincidir exactamente.",inside);
const code=box("Código de verificación",inside,f);code.paddingTop=16;code.paddingBottom=16;rounded(code);paint(code,"accentSoft");
const sas=txt(code,"ABCD 2345 EF67 89AB",inside,"label","accentInk");sas.fontName=mono;sas.fontSize=22;sas.textAlignHorizontal="CENTER";
txt(f,"Se aprueban los 3 archivos de este lote. Los próximos envíos requieren otra confirmación.",inside);
txt(f,"Si no reconoces algún archivo o la verificación no coincide, rechaza la solicitud.",inside,"small","muted");
if(outgoing){
const check=box("Comprobación explícita",inside,f,"HORIZONTAL");check.counterAxisAlignItems="CENTER";check.itemSpacing=12;
const square=figma.createRectangle();square.name="Casilla pendiente";square.resize(18,18);square.fills=[];square.strokes=[{type:"SOLID",color:rgb("#0A6B77")}];square.strokeWeight=2;square.cornerRadius=2;check.appendChild(square);
txt(check,"Comparé el código completo y coincide.",inside-30);
}
txt(f,"Solicitud válida durante 1:30",inside,"small","muted");
const button=box("Acción confirmada",inside,f);button.paddingTop=14;button.paddingBottom=14;rounded(button);paint(button,"accent");
if(outgoing)button.opacity=0.45;
const label=txt(button,outgoing?"Coincide: enviar 3 archivos":"Coincide: recibir 3 archivos",inside,"label","surface");label.textAlignHorizontal="CENTER";
const reject=txt(f,"Rechazar",inside,"label","accent");reject.textAlignHorizontal="CENTER";
const component=figma.createComponentFromNode(f);component.description="Confirmación bilateral para el manifiesto exacto de un lote. Lista de archivos y contenido desplazables cuando falta espacio; título y acciones permanecen visibles. Un fallo no deshace archivos ya guardados. Referencia UX, no captura.";
const instance=component.createInstance();holder.appendChild(instance);
return {componentId:component.id,instanceId:instance.id};
};
const sender=makeDialog(500,true,120);const receiver=makeDialog(420,false,720);
txt(board,"Referencia editable · Datos y clave sintéticos · No es una captura de la aplicación",1000,"small","muted");
for(const top of page.children){created.push(top.id);if("findAll" in top)created.push(...top.findAll().map(n=>n.id));}
const counts={};for(const n of board.findAll())counts[n.type]=(counts[n.type]||0)+1;
return {createdNodeIds:[...new Set(created)],mutatedNodeIds:mutated,pageId:page.id,boardId:board.id,sender,receiver,collectionId:collection.id,variables:Object.fromEntries(Object.entries(vars).map(([k,v])=>[k,v.id])),textStyles:Object.fromEntries(Object.entries(styles).map(([k,s])=>[k,s.id])),bounds:{width:board.width,height:board.height},nodeTypeCounts:counts,imageFilledNodes:board.findAll(n=>"fills"in n&&Array.isArray(n.fills)&&n.fills.some(p=>p.type==="IMAGE")).map(n=>n.id),fontFamilies:[...new Set(board.findAll(n=>n.type==="TEXT").map(n=>n.fontName.family))]};
