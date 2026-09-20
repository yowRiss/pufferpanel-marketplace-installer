import{u as $,r as V,$ as b,q as j,y as w,c as x,a as S,d as l,h as s,g as T,k as d,l as C,o as U,b as i,B,C as I,t as N}from"./index-BHf2KLRR.js";import{_ as O,a as R}from"./Environment-CGfODcAG.js";import{_ as D,a as E,b as q,c as J}from"./RunConfig-N-PKHlkZ.js";import{_ as L}from"./Ace-DeYvJJVQ.js";import{T as G,_ as m}from"./Tab-B9baEc6A.js";import"./Dropdown-DO2aVbGS.js";import"./EnvironmentConfig-gwWJovWl.js";import"./Suggestion-CTErE9RL.js";import"./Toggle-CCA8PwBX.js";const H={class:"templatecreate"},Y={__name:"TemplateCreate",setup(M){const p=d("api"),_=d("toast"),v=d("events"),{t:o}=$(),y=w(),k=C();let r=`{
    "name": "",
    "display": "",
    "type": "",
    "data": {},
    "groups": [],
    "install": [],
    "run": {
        "command": "",
        "stop": "",
        "workingDirectory": "",
        "pre": [],
        "post": [],
        "environmentVars": {}
    },
    "environment": {
        "type": "host"
    },
    "supportedEnvironments": [
      { "type": "host" }
    ]
}`;const a=V(r),u=V({general:!1,run:!1});let f=!1;b(()=>f||r===a.value?!0:new Promise(n=>{v.emit("confirm",o("common.ConfirmLeave"),{text:o("common.Discard"),icon:"remove",color:"error",action:()=>{n(!0)}},{color:"primary",action:()=>{n(!1)}})})),j(()=>{if(y.query.copy){const n=sessionStorage.getItem("copiedTemplate");n&&(a.value=n,sessionStorage.removeItem("copiedTemplate"),setTimeout(()=>{r=a.value},50))}});async function g(){if(!c())return;const n=JSON.parse(a.value).name,e=await p.template.exists(0,n),t=async()=>{await p.template.save(n,a.value),_.success(o("templates.Saved")),f=!0,k.push({name:"TemplateView",params:{repo:0,id:n}})};e?v.emit("confirm",o("templates.ConfirmOverwrite"),{text:o("templates.Overwrite"),icon:"check",action:()=>{t()}}):t()}function c(){return Object.values(u.value).filter(n=>n===!1).length===0}return(n,e)=>(U(),x("div",H,[S("div",null,[l(G,{anchors:""},{default:s(()=>[l(m,{id:"general",title:i(o)("templates.General"),icon:"general",hotkey:"t g"},{default:s(()=>[l(O,{modelValue:a.value,"onUpdate:modelValue":e[0]||(e[0]=t=>a.value=t),"id-editable":"",onValid:e[1]||(e[1]=t=>u.value.general=t)},null,8,["modelValue"])]),_:1},8,["title"]),l(m,{id:"variables",title:i(o)("templates.Variables"),icon:"variables",hotkey:"t v"},{default:s(()=>[l(D,{modelValue:a.value,"onUpdate:modelValue":e[2]||(e[2]=t=>a.value=t)},null,8,["modelValue"])]),_:1},8,["title"]),l(m,{id:"install",title:i(o)("templates.Install"),icon:"install",hotkey:"t i"},{default:s(()=>[l(E,{modelValue:a.value,"onUpdate:modelValue":e[3]||(e[3]=t=>a.value=t)},null,8,["modelValue"])]),_:1},8,["title"]),l(m,{id:"run",title:i(o)("templates.RunConfig"),icon:"start",hotkey:"t r"},{default:s(()=>[l(q,{modelValue:a.value,"onUpdate:modelValue":e[4]||(e[4]=t=>a.value=t),onValid:e[5]||(e[5]=t=>u.value.run=t)},null,8,["modelValue"])]),_:1},8,["title"]),l(m,{id:"hooks",title:i(o)("templates.Hooks"),icon:"hooks",hotkey:"t h"},{default:s(()=>[l(J,{modelValue:a.value,"onUpdate:modelValue":e[6]||(e[6]=t=>a.value=t)},null,8,["modelValue"])]),_:1},8,["title"]),l(m,{id:"environment",title:i(o)("templates.Environment"),icon:"environment",hotkey:"t e"},{default:s(()=>[l(R,{modelValue:a.value,"onUpdate:modelValue":e[7]||(e[7]=t=>a.value=t)},null,8,["modelValue"])]),_:1},8,["title"]),l(m,{id:"json",title:i(o)("templates.Json"),icon:"json",hotkey:"t j"},{default:s(()=>[l(L,{id:"template-json",modelValue:a.value,"onUpdate:modelValue":e[8]||(e[8]=t=>a.value=t),class:"template-json-editor",mode:"json"},null,8,["modelValue"])]),_:1},8,["title"])]),_:1}),l(T,{color:"primary",disabled:!c(),onClick:e[9]||(e[9]=t=>g())},{default:s(()=>[l(I,{name:"save"}),B(N(i(o)("common.Save")),1)]),_:1},8,["disabled"])])]))}};export{Y as default};
//# sourceMappingURL=TemplateCreate-BoAbZqFo.js.map
