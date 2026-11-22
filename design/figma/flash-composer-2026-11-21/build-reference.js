const page=await figma.getNodeByIdAsync('27:63'); await figma.setCurrentPageAsync(page);
if(page.children.length) throw new Error('Page is not empty. Inspect before retry.');
const V=id=>$fig.getVar('VariableID:'+id);
const ink=V('4:9'), muted=V('4:10'), primary=V('4:11'), soft=V('4:12'), white=V('4:7'), mobileSurface=V('4:8'), canvas=V('4:14'), gap=V('4:15'), pad=V('4:16'), radius=V('4:17');
const foundation=$fig.varCollection({name:'Flash compositor · layout',modes:['Default']});
const space8=foundation.numVar({name:'space/8',values:{Default:8},scopes:['GAP']});
const space12=foundation.numVar({name:'space/12',values:{Default:12},scopes:['GAP']});
const radius8=foundation.numVar({name:'radius/8',values:{Default:8},scopes:['CORNER_RADIUS']});
const outline=foundation.colorVar({name:'color/outlineVariant',values:{Default:'#C3D2DC'},scopes:['STROKE_COLOR']});
const paint=v=>[{type:'SOLID',color:v}];
const font=style=>({family:'Inter',style});
const tx=(s,size=14,style='Regular',color=ink,o={})=>$fig.text({characters:s,fontName:font(style),fontSize:size,lineHeight:{unit:'PIXELS',value:size<=12?18:size<=14?22:size<=18?26:32},fills:paint(color),...o});
const row=(name,children,o={})=>$fig.autoLayout({name,layoutMode:'HORIZONTAL',itemSpacing:space8,counterAxisAlignItems:'CENTER',fills:[],...o},children);
const col=(name,children,o={})=>$fig.autoLayout({name,layoutMode:'VERTICAL',itemSpacing:space8,fills:[],...o},children);
const fill={layoutSizingHorizontal:'FILL'};
const border={strokes:paint(outline),strokeWeight:1};
const iconPaths={
Flash:'M13 2L5 14H11L10 22L19 9H13L13 2Z',
File:'M6 3H14L19 8V19Q19 21 17 21H6Q4 21 4 19V5Q4 3 6 3Z M14 3V8H19 M8 13H15 M8 17H12',
Devices:'M3 4H15Q17 4 17 6V13H3V6Q3 4 5 4 M3 13V15Q3 17 5 17H11 M8 17V20 M5 20H11 M16 10H20Q21 10 21 11V20Q21 21 20 21H16Q15 21 15 20V11Q15 10 16 10Z M18 18H18.01',
Copy:'M9 8H18Q20 8 20 10V19Q20 21 18 21H9Q7 21 7 19V10Q7 8 9 8Z M16 8V5Q16 3 14 3H5Q3 3 3 5V14Q3 16 5 16H7',
Close:'M6 6L18 18 M18 6L6 18'
};
const icons={};
for(const [name,path] of Object.entries(iconPaths)){icons[name]=$fig.svgComponent('<svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="'+path+'" stroke="#536672" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"/></svg>',{name:'Flash/Icon/'+name});}
const icon=n=>$fig.instance(icons[n]);
const components=[];
const buttons={};
for(const platform of ['PC','Android']){
 for(const kind of ['Primary','Secondary','Text']){
  const b=$fig.component({name:'Flash/Button/'+platform+'/'+kind,layoutMode:'HORIZONTAL',height:48,paddingLeft:16,paddingRight:16,cornerRadius:platform==='PC'?radius8:radius,primaryAxisAlignItems:'CENTER',counterAxisAlignItems:'CENTER',fills:kind==='Primary'?paint(primary):[],...(kind==='Secondary'?border:{})},[tx('Acción',platform==='PC'?13:14,'Semi Bold',kind==='Primary'?white:primary).textProp('Label')]);
  buttons[platform+kind]=b;components.push(b);
 }
}
const btn=(label,platform='PC',kind='Primary',opts={})=>$fig.instance(buttons[platform+kind],{props:{Label:label},...opts});
const receiver=$fig.component({name:'Flash/Receiver',layoutMode:'HORIZONTAL',width:420,height:64,paddingLeft:12,paddingRight:4,paddingTop:4,paddingBottom:4,itemSpacing:space12,counterAxisAlignItems:'CENTER',cornerRadius:radius8,fills:paint(soft)},[
 icon('Devices'),
 col('Receiver text',[tx('Para Laptop 1',14,'Semi Bold').textProp('Receiver'),tx('Identidad por verificar',12,'Regular',muted).textProp('Identity')],fill),
 row('Change receiver',[tx('Cambiar',14,'Semi Bold',primary).textProp('Change')],{height:48,paddingLeft:12,paddingRight:12})
]);
components.push(receiver);
const fileRow=$fig.component({name:'Flash/File row',layoutMode:'HORIZONTAL',width:420,height:48,itemSpacing:space8,counterAxisAlignItems:'CENTER',fills:[]},[
 icon('File'),tx('Resumen.pdf',13,'Medium',ink,{...fill,textAutoResize:'HEIGHT'}).textProp('Filename'),
 tx('1.8 MB',12,'Regular',muted).textProp('Size'),
 row('Remove file',[icon('Close')],{name:'Remove file · 48 dp target',width:48,height:48,primaryAxisAlignItems:'CENTER'})
]);
components.push(fileRow);
const editor=$fig.component({name:'Flash/Editor',layoutMode:'VERTICAL',width:420,height:120,paddingLeft:12,paddingRight:12,paddingTop:8,paddingBottom:12,itemSpacing:space8,cornerRadius:radius8,fills:[],...border},[
 tx('Mensaje',12,'Medium',primary).textProp('Label'),
 tx('Te comparto los apuntes de hoy.',14,'Regular',ink,{...fill,textAutoResize:'HEIGHT'}).textProp('Draft')
]);
components.push(editor);
const session=$fig.component({name:'Flash/Session summary',layoutMode:'HORIZONTAL',width:420,height:48,itemSpacing:space8,counterAxisAlignItems:'CENTER',fills:[]},[
 icon('Flash'),tx('Flash activo · 24:18',14,'Semi Bold',primary,fill).textProp('Status'),row('Session details',[tx('Detalles',14,'Semi Bold',primary).textProp('Details')],{height:48,paddingLeft:12,paddingRight:12})
]);components.push(session);
const activity=$fig.component({name:'Flash/Activity text',layoutMode:'VERTICAL',width:260,itemSpacing:space8,fills:[]},[
 tx('Texto recibido · 10:42',12,'Medium',muted,{...fill,textAutoResize:'HEIGHT'}).textProp('Meta'),
 tx('Nos vemos a las 16:00.',14,'Regular',ink,{...fill,textAutoResize:'HEIGHT'}).textProp('Preview'),
 row('Activity actions',[row('View text',[tx('Ver texto',14,'Semi Bold',primary).textProp('View')],{height:48,paddingRight:16}),row('Copy text',[tx('Copiar',14,'Semi Bold',primary).textProp('Copy')],{height:48})],fill)
]);components.push(activity);
const L={
 ES:{session:'Flash activo · 24:18',details:'Detalles',back:'Volver a Qetara',stop:'Desactivar',toPC:'Para Laptop 1',toPhone:'Para Android 02',identity:'Identidad por verificar',change:'Cambiar',message:'Mensaje',draft:'Te comparto los apuntes de hoy.',attach:'Adjuntar',paste:'Pegar',tray:'2 archivos · 2.4 MB',clear:'Quitar todos',sendText:'Enviar mensaje',sendFiles:'Enviar 2 archivos',separate:'El mensaje y los archivos se envían por separado.',verify:'Cada envío se confirma en ambos equipos.',status:'Listo para enviar. Verificarás el código antes de continuar.',activity:'Actividad reciente',meta:'Texto recibido · 10:42',preview:'Nos vemos a las 16:00.',view:'Ver texto',copy:'Copiar',history:'Ver actividad',empty:'Escribe un mensaje o adjunta archivos.',textOnly:'Solo mensaje',filesOnly:'Solo archivos',both:'Mensaje y archivos',none:'Vacío',synthetic:'Referencia editable · Contenido de ejemplo · Tema claro frío'},
 EN:{session:'Flash active · 24:18',details:'Details',back:'Back to Qetara',stop:'Turn off',toPC:'To Laptop 1',toPhone:'To Android 02',identity:'Identity not yet verified',change:'Change',message:'Message',draft:'Here are today’s notes.',attach:'Attach',paste:'Paste',tray:'2 files · 2.4 MB',clear:'Remove all',sendText:'Send message',sendFiles:'Send 2 files',separate:'The message and files are sent separately.',verify:'Each send is confirmed on both devices.',status:'Ready to send. You will verify the code before continuing.',activity:'Recent activity',meta:'Text received · 10:42',preview:'See you at 4:00 PM.',view:'View text',copy:'Copy',history:'View activity',empty:'Write a message or attach files.',textOnly:'Message only',filesOnly:'Files only',both:'Message and files',none:'Empty',synthetic:'Editable reference · Sample content · Cool light theme'}
};
const activityPanel=(lang,mobile=false)=>{
 const l=L[lang];
 return col('Secondary activity',[
  row('Activity heading',[tx(l.activity,14,'Semi Bold',ink,fill),...(!mobile?[tx('10:42',12,'Regular',muted)]:[])],fill),
  $fig.instance(activity,{...fill,props:{Meta:l.meta,Preview:l.preview,View:l.view,Copy:l.copy}})
 ],{width:mobile?358:288,paddingLeft:mobile?0:16,paddingRight:mobile?0:16,paddingTop:mobile?0:16,paddingBottom:mobile?0:16,itemSpacing:space12,fills:mobile?[]:paint(white),cornerRadius:radius,...(!mobile?border:{})});
};
function composer(lang,mobile=false,state='Both'){
 const l=L[lang],platform=mobile?'Android':'PC';
 const files=state==='Files'||state==='Both',text=state==='Text'||state==='Both';
 const children=[
  $fig.instance(receiver,{...fill,height:64,props:{Receiver:mobile?l.toPC:l.toPhone,Identity:l.identity,Change:l.change}}),
  $fig.instance(editor,{...fill,height:mobile?96:152,props:{Label:l.message,Draft:text?l.draft:l.empty}}),
  row('Attach and paste',[btn(l.attach,platform,'Secondary'),btn(l.paste,platform,'Text')],fill)
 ];
 if(files) children.push(col('Attachment tray',[
  row('Attachment total',[tx(l.tray,13,'Semi Bold',ink,fill),btn(l.clear,platform,'Text')],fill),
  $fig.instance(fileRow,{...fill,props:{Filename:lang==='ES'?'Apuntes.pdf':'Notes.pdf',Size:'1.8 MB'}}),
  $fig.instance(fileRow,{...fill,props:{Filename:'Diagrama.png',Size:'0.6 MB'}})
 ],{...fill,paddingLeft:12,paddingRight:8,paddingTop:4,paddingBottom:4,itemSpacing:0,cornerRadius:radius8,fills:paint(canvas)}));
 const actions=[];
 if(text) actions.push(btn(l.sendText,platform,files?'Secondary':'Primary',mobile?fill:{}));
 if(files) actions.push(btn(l.sendFiles,platform,'Primary',mobile?fill:{}));
 if(actions.length) children.push(row('Separate send actions',actions,{...fill,itemSpacing:space8}));
 if(state==='Both') children.push(tx(l.separate,12,'Regular',muted,{...fill,textAutoResize:'HEIGHT'}));
 children.push(tx(l.verify,12,'Regular',muted,{...fill,textAutoResize:'HEIGHT'}));
 if(!mobile) children.push(col('Status in place',[tx(l.status,13,'Regular',muted,{...fill,textAutoResize:'HEIGHT'})],{...fill,paddingTop:8}));
 return col('Flash composer · '+state,children,{width:mobile?390:680,paddingLeft:mobile?16:24,paddingRight:mobile?16:24,paddingTop:mobile?12:24,paddingBottom:mobile?16:24,itemSpacing:space8,cornerRadius:radius,fills:paint(mobile?mobileSurface:white),...border});
}
function desktop(lang){
 const l=L[lang];
 return col('PC · '+lang+' · Message and files',[
 row('App title',[tx('Qetara',14,'Semi Bold',ink,fill),btn(l.back,'PC','Text')],fill),
 row('Session toolbar',[$fig.instance(session,{layoutSizingHorizontal:'FILL',props:{Status:l.session,Details:l.details}}),btn(l.stop,'PC','Secondary')],fill),
 row('Workspace',[composer(lang),activityPanel(lang)],{...fill,itemSpacing:gap,counterAxisAlignItems:'MIN'}),
 tx(l.synthetic,12,'Regular',muted)
 ],{width:1040,height:844,layoutSizingHorizontal:'FIXED',layoutSizingVertical:'FIXED',paddingLeft:pad,paddingRight:pad,paddingTop:16,paddingBottom:16,itemSpacing:gap,fills:paint(canvas)});
}
function mobile(lang){
 const l=L[lang];
 return col('Android · '+lang+' · Message and files',[
 row('Top app bar',[tx('Qetara / Flash',22,'Semi Bold')],{...fill,paddingLeft:16,paddingRight:16,height:56}),
 col('Session and secondary activity',[
  $fig.instance(session,{...fill,props:{Status:l.session,Details:l.details}}),
  activityPanel(lang,true)
 ],{...fill,paddingLeft:16,paddingRight:16,itemSpacing:space8}),
 $fig.frame({name:'Flexible activity scroll region',layoutSizingHorizontal:'FILL',layoutSizingVertical:'FILL',fills:[]}),
 composer(lang,true),
 row('Android gesture area',[$fig.rectangle({width:110,height:4,cornerRadius:2,fills:paint(muted)})],{...fill,height:20,primaryAxisAlignItems:'CENTER'})
 ],{width:390,height:844,layoutSizingHorizontal:'FIXED',layoutSizingVertical:'FIXED',itemSpacing:0,fills:paint(canvas)});
}
const screens=[];
const rows=[];
for(const lang of ['ES','EN']){
 const d=desktop(lang),m=mobile(lang);screens.push(d,m);
 rows.push(col(lang,[tx(lang==='ES'?'Flash · Español · Adjuntos y actividad expandidos':'Flash · English · Attachments and activity expanded',24,'Semi Bold'),row('PC and Android '+lang,[d,m],{itemSpacing:24,counterAxisAlignItems:'MIN'})],{itemSpacing:12}));
}
const stateCards=[];
for(const lang of ['ES','EN']){
const l=L[lang];stateCards.push(col('Action states '+lang,[
tx(lang==='ES'?'Acciones según contenido':'Actions follow the content',18,'Semi Bold'),
row('States',[
 col(l.none,[tx(l.none,14,'Semi Bold'),tx(l.empty,12,'Regular',muted,{width:200,textAutoResize:'HEIGHT'})],{width:220}),
 col(l.textOnly,[tx(l.textOnly,14,'Semi Bold'),btn(l.sendText)],{width:220}),
 col(l.filesOnly,[tx(l.filesOnly,14,'Semi Bold'),btn(l.sendFiles)],{width:220}),
 col(l.both,[tx(l.both,14,'Semi Bold'),row('Two independent actions',[btn(l.sendText,'PC','Secondary'),btn(l.sendFiles)])],{width:400})
],{itemSpacing:24})
],{itemSpacing:12}));}
const review=col('Flash compositor · Review ES + EN',[
 tx('Flash · Compositor / Composer',32,'Semi Bold'),
 tx('PC + Android · ES / EN · 21 Nov 2026',14,'Regular',muted),
 ...rows,...stateCards,
 tx('Android: compositor sobre el IME; área de actividad desplazable. / Composer stays above the IME; activity scrolls.',14,'Regular',muted),
 tx('La confirmación bilateral por lote conserva los componentes 4:43 y 4:76. / Bilateral per-batch confirmation keeps components 4:43 and 4:76.',14,'Regular',muted)
],{x:0,y:0,paddingLeft:32,paddingRight:32,paddingTop:32,paddingBottom:32,itemSpacing:24,fills:paint(white)});
const assets=col('Local reusable components · source aligned',[...Object.values(icons),...components],{x:1700,y:0,itemSpacing:24});
await $fig.done();
const all=review.node.query('*').toArray(); const counts={}; for(const n of all) counts[n.type]=(counts[n.type]||0)+1;
const textNodes=review.node.query('TEXT').toArray(); const mismatches=textNodes.filter(n=>n.fontName.family!=='Inter').map(n=>({id:n.id,font:n.fontName}));
const images=all.filter(n=>'fills'in n&&Array.isArray(n.fills)&&n.fills.some(p=>p.type==='IMAGE')).map(n=>({id:n.id,name:n.name,type:n.type,width:n.width,height:n.height}));
return {pageId:page.id,reviewId:review.node.id,screens:screens.map(s=>({id:s.node.id,name:s.node.name,width:s.node.width,height:s.node.height})),components:components.map(c=>({id:c.node.id,name:c.node.name})),icons:Object.values(icons).map(c=>({id:c.node.id,name:c.node.name})),counts,totalDescendants:all.length,textCount:textNodes.length,fontMismatches:mismatches,imageFilledNodes:images,wrapper:{width:review.node.width,height:review.node.height}};
