import{d as K,k as Q,r,i as W,O as Y,N as Z,c as E,j as ee,l as m,p as s,e as f,b as I,u as i,s as c,h as L,q as C,F as N,v as O,a as te,o as d,a7 as oe}from"./index-BlNg8JvZ.js";import{_ as se}from"./Input.vue_vue_type_script_setup_true_lang-DgxhStwU.js";import{_ as A}from"./index-Bxlg1VJc.js";import{b as le,e as ae,f as ie}from"./request-CsQxgJX2.js";import{t as v}from"./index-BQC8Ide7.js";import{g as ne,h as re,i as ue,f as ce}from"./solution-DRQEhqqB.js";import{c as D}from"./problem-Sd91issY.js";import{e as de,a as me}from"./submission-DiounJ3l.js";/* empty css                      */import{_ as pe}from"./MarkdownEdit.vue_vue_type_script_setup_true_lang-48vestKy.js";import{T as fe,M as ve,S as he}from"./index-DUMiZlrB.js";import{A as ge}from"./arrow-left-Cnd2fpWh.js";import{C as be}from"./check-B9EfdoX-.js";import{X as xe}from"./x-q_I1Z5CT.js";import"./utils-CORwZsi7.js";import"./markdown-Ds2LzS9j.js";async function _e(){return le("/solution-topics")}const ye={class:"flex h-screen w-full flex-col overflow-hidden bg-background"},we={class:"flex h-14 flex-shrink-0 items-center border-b px-4"},ke={class:"text-xs text-muted-foreground"},$e={class:"flex flex-1 overflow-hidden"},Se={class:"flex w-full flex-col overflow-hidden"},Ce={class:"flex flex-shrink-0 flex-col gap-3 px-4 py-3"},Te={class:"rounded-lg border bg-card p-3"},Fe={class:"mt-3 flex flex-wrap items-center gap-2"},Ve={class:"relative"},je={key:0,class:"absolute left-0 top-10 z-50 w-80 rounded-md border border-border bg-card shadow-lg"},Be={class:"border-b border-border px-4 py-3"},Ee={class:"text-sm font-medium"},Ie={key:0,class:"flex items-center justify-center py-8"},Le={class:"text-sm text-muted-foreground"},Ne={key:1,class:"py-8 text-center"},Oe={class:"text-sm text-destructive"},Ae={key:2,class:"py-8 text-center"},De={class:"text-sm text-muted-foreground"},He={key:3,class:"max-h-64 overflow-y-auto"},Me={class:"p-2"},Pe=["onClick"],Ue=["onClick"],ze={class:"flex-1 px-4 pb-4 overflow-hidden"},qe={class:"grid h-full grid-cols-2 gap-4"},Re={class:"flex flex-col rounded-lg border bg-card overflow-hidden"},Ge={class:"flex items-center border-b bg-muted/30 px-3 py-2 text-xs font-medium text-muted-foreground"},Xe={class:"flex-1 overflow-y-auto p-4"},dt=K({__name:"SolutionsEditView",setup(Je){const h=Z(),x=Y(),{t:e}=Q(),w=r("java"),g=r(""),B=`# ${e("solution.template.approach")}

> ${e("solution.template.approachHint")}

# ${e("solution.template.solution")}

> ${e("solution.template.solutionHint")}

# ${e("solution.template.complexity")}

- ${e("solution.template.timeComplexity")}: $O(*)$
- ${e("solution.template.spaceComplexity")}: $O(*)$

# ${e("solution.template.code")}

\`\`\`java {group="solution"}
class Solution {
   public int[] twoSum(int[] nums, int target) {
       for (int i = 0; i < nums.length; i++) {
           for (int j = i + 1; j < nums.length; j++) {
               if (nums[i] + nums[j] == target) {
                   return new int[] { i, j };
               }
           }
       }
       return new int[] {};
   }
}
\`\`\`
`,p=r(""),T=r(B),u=r(""),_=r(""),y=r(!1),F=r("");W(async()=>{x.name==="solution-edit"&&x.params.id?(y.value=!0,F.value=x.params.id,await H(F.value)):(u.value=x.params.id,await M()),U()});const H=async o=>{try{const t=await ne(o);if(g.value=t.title,p.value=t.content,T.value=t.content,w.value=t.language,t.tags&&(n.value=t.tags),u.value=t.problem_id.toString(),u.value){const l=await D(u.value);_.value=l.slug}}catch(t){console.error("Failed to load solution",t),v.error(e("solution.messages.loadFailed")),h.back()}},M=async()=>{const o=x.query.submissionId;let t=null,l=B;if(o)try{t=await de(o)}catch(a){console.error("Failed to fetch submission",a),v.error(e("solution.messages.fetchSubmissionFailed")),h.back();return}else if(u.value)try{const a=await me(u.value);a&&a.status==="Accepted"&&(t=a)}catch(a){console.log("No best submission found or failed to fetch",a)}if(t)if(t.status!=="Accepted"){if(o){v.error(e("solution.messages.acceptedRequired")),h.push({name:"problem-detail",params:{slug:_.value||t.problem_id.toString(),tab:"solution"}});return}}else{u.value||(u.value=t.problem_id.toString());const a=t.language.toLowerCase();w.value=a;const S=t.code;l=`# ${e("solution.template.approach")}

> ${e("solution.template.approachHint")}

# ${e("solution.template.solution")}

> ${e("solution.template.solutionHint")}

# ${e("solution.template.complexity")}

- ${e("solution.template.timeComplexity")}: $O(*)$
- ${e("solution.template.spaceComplexity")}: $O(*)$

# ${e("solution.template.code")}

\`\`\`${a} {group="solution"}
${S}
\`\`\`
`}if(p.value=l,T.value=l,u.value)try{const a=await D(u.value);_.value=a.slug}catch(a){console.error("Failed to fetch problem detail",a)}},k=r([]),n=r([]),P=E(()=>k.value.filter(o=>n.value.includes(o.id))),V=r(!1),j=r(!1),$=r(null),U=async()=>{j.value=!0,$.value=null;try{const{topics:o}=await _e();k.value=o,!n.value.length&&o.length&&!y.value&&(n.value=[o[0].id])}catch(o){console.error("Failed to load solution topics",o),$.value=e("solution.messages.loadTopicsFailed")}finally{j.value=!1}},b=r(!0),z=E(()=>b.value?e("solution.editor.draftSaved"):e("solution.editor.editingDraft")),q=oe(()=>{b.value=!0},800);ee([g,p,n],()=>{b.value=!1,q()});const R=o=>{n.value.includes(o)?n.value=n.value.filter(t=>t!==o):n.value=[...n.value,o]},G=o=>{n.value=n.value.filter(t=>t!==o)},X=async()=>{if(!g.value.trim()){v.error(e("solution.messages.enterTitle"));return}if(!p.value.trim()){v.error(e("solution.messages.enterContent"));return}b.value=!1;try{y.value?(await re(F.value,{title:g.value,content:p.value,language:w.value,tags:n.value}),v.success(e("solution.messages.updateSuccess"))):(await ue(u.value,{title:g.value,content:p.value,language:w.value,tags:n.value}),v.success(e("solution.messages.publishSuccess"))),b.value=!0,_.value?h.push({name:"problem-detail",params:{slug:_.value,tab:"solution"}}):h.back()}catch(o){console.error("Failed to publish/update solution",o);let t=e("solution.messages.publishFailed");if(ae.isAxiosError(o)&&(t=o.response?.data?.message||t),!y.value&&t.toLowerCase().includes("already exists")&&u.value){const l=ie();if(l)try{const S=(await ce(l,u.value)).items[0];if(S){v.info(e("solution.messages.alreadyExists")),h.push({name:"solution-edit",params:{id:S.id}});return}}catch(a){console.error("Failed to fetch existing solution",a)}}v.error(t),b.value=!0}},J=()=>{h.back()};return(o,t)=>(d(),m("div",ye,[s("header",we,[f(i(A),{variant:"ghost",size:"sm",class:"gap-2",onClick:J},{default:I(()=>[f(i(ge),{class:"h-4 w-4"}),C(" "+c(i(e)("solution.editor.back")),1)]),_:1}),t[3]||(t[3]=s("div",{class:"flex-1"},null,-1)),s("span",ke,c(z.value),1),f(i(A),{size:"sm",class:"ml-4 gap-2",onClick:X},{default:I(()=>[f(i(he),{class:"h-4 w-4"}),C(" "+c(y.value?i(e)("solution.editor.update"):i(e)("solution.editor.publish")),1)]),_:1})]),s("main",$e,[s("div",Se,[s("div",Ce,[s("div",Te,[f(i(se),{modelValue:g.value,"onUpdate:modelValue":t[0]||(t[0]=l=>g.value=l),placeholder:i(e)("solution.editor.enterTitle"),class:"rounded-none border-0 border-b bg-transparent px-0 text-base font-medium shadow-none focus-visible:ring-0"},null,8,["modelValue","placeholder"]),s("div",Fe,[s("div",Ve,[s("button",{type:"button",class:"flex h-8 items-center gap-2 rounded-md border border-border bg-background px-3 text-sm hover:bg-muted",onClick:t[1]||(t[1]=l=>V.value=!V.value)},[f(i(fe),{class:"h-4 w-4"}),C(" "+c(i(e)("solution.editor.topics")),1)]),V.value?(d(),m("div",je,[s("div",Be,[s("h4",Ee,c(i(e)("solution.editor.selectTopics")),1)]),j.value?(d(),m("div",Ie,[s("span",Le,c(i(e)("solution.editor.loading")),1)])):$.value?(d(),m("div",Ne,[s("p",Oe,c($.value),1)])):k.value.length?(d(),m("div",He,[s("div",Me,[(d(!0),m(N,null,O(k.value,l=>(d(),m("button",{key:l.id,type:"button",class:"flex w-full items-center justify-between rounded px-2 py-1.5 text-sm hover:bg-muted",onClick:a=>R(l.id)},[s("span",null,c(l.name),1),n.value.includes(l.id)?(d(),te(i(be),{key:0,class:"h-4 w-4"})):L("",!0)],8,Pe))),128))])])):(d(),m("div",Ae,[s("p",De,c(i(e)("solution.editor.noTopics")),1)]))])):L("",!0)]),(d(!0),m(N,null,O(P.value,l=>(d(),m("span",{key:l.id,class:"inline-flex items-center gap-1.5 rounded-md bg-secondary px-2 py-1 text-sm"},[C(c(l.name)+" ",1),s("button",{type:"button",class:"inline-flex h-4 w-4 items-center justify-center hover:opacity-70",onClick:a=>G(l.id)},[f(i(xe),{class:"h-3 w-3"})],8,Ue)]))),128))])])]),s("div",ze,[s("div",qe,[f(i(pe),{modelValue:p.value,"onUpdate:modelValue":t[2]||(t[2]=l=>p.value=l),"default-value":T.value},null,8,["modelValue","default-value"]),s("div",Re,[s("div",Ge,c(i(e)("solution.editor.preview")),1),s("div",Xe,[f(i(ve),{content:p.value},null,8,["content"])])])])])])])]))}});export{dt as default};
