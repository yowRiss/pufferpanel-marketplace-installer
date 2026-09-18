import{u as q,k as M,r as f,q as N,z as V,A as W,c as x,o as d,a as C,w as E,e as v,t as D,b as U,f as H,C as L,d as O,i as y,x as R,_ as F}from"./index-6aFRqPP2.js";const B=`(function(){"use strict";var c=function(i,e){return Object.defineProperty?Object.defineProperty(i,"raw",{value:e}):i.raw=e,i},r;(function(i){i[i.EOS=0]="EOS",i[i.Text=1]="Text",i[i.Incomplete=2]="Incomplete",i[i.ESC=3]="ESC",i[i.Unknown=4]="Unknown",i[i.SGR=5]="SGR",i[i.OSCURL=6]="OSCURL"})(r||(r={}));class w{constructor(){this.VERSION="6.0.6",this.setup_palettes(),this._use_classes=!1,this.bold=!1,this.faint=!1,this.italic=!1,this.underline=!1,this.fg=this.bg=null,this._buffer="",this._url_allowlist={http:1,https:1},this._escape_html=!0,this.boldStyle="font-weight:bold",this.faintStyle="opacity:0.7",this.italicStyle="font-style:italic",this.underlineStyle="text-decoration:underline"}set use_classes(e){this._use_classes=e}get use_classes(){return this._use_classes}set url_allowlist(e){this._url_allowlist=e}get url_allowlist(){return this._url_allowlist}set escape_html(e){this._escape_html=e}get escape_html(){return this._escape_html}set boldStyle(e){this._boldStyle=e}get boldStyle(){return this._boldStyle}set faintStyle(e){this._faintStyle=e}get faintStyle(){return this._faintStyle}set italicStyle(e){this._italicStyle=e}get italicStyle(){return this._italicStyle}set underlineStyle(e){this._underlineStyle=e}get underlineStyle(){return this._underlineStyle}setup_palettes(){this.ansi_colors=[[{rgb:[0,0,0],class_name:"ansi-black"},{rgb:[187,0,0],class_name:"ansi-red"},{rgb:[0,187,0],class_name:"ansi-green"},{rgb:[187,187,0],class_name:"ansi-yellow"},{rgb:[0,0,187],class_name:"ansi-blue"},{rgb:[187,0,187],class_name:"ansi-magenta"},{rgb:[0,187,187],class_name:"ansi-cyan"},{rgb:[255,255,255],class_name:"ansi-white"}],[{rgb:[85,85,85],class_name:"ansi-bright-black"},{rgb:[255,85,85],class_name:"ansi-bright-red"},{rgb:[0,255,0],class_name:"ansi-bright-green"},{rgb:[255,255,85],class_name:"ansi-bright-yellow"},{rgb:[85,85,255],class_name:"ansi-bright-blue"},{rgb:[255,85,255],class_name:"ansi-bright-magenta"},{rgb:[85,255,255],class_name:"ansi-bright-cyan"},{rgb:[255,255,255],class_name:"ansi-bright-white"}]],this.palette_256=[],this.ansi_colors.forEach(t=>{t.forEach(s=>{this.palette_256.push(s)})});let e=[0,95,135,175,215,255];for(let t=0;t<6;++t)for(let s=0;s<6;++s)for(let a=0;a<6;++a){let l={rgb:[e[t],e[s],e[a]],class_name:"truecolor"};this.palette_256.push(l)}let n=8;for(let t=0;t<24;++t,n+=10){let s={rgb:[n,n,n],class_name:"truecolor"};this.palette_256.push(s)}}escape_txt_for_html(e){return this._escape_html?e.replace(/[&<>"']/gm,n=>{if(n==="&")return"&amp;";if(n==="<")return"&lt;";if(n===">")return"&gt;";if(n==='"')return"&quot;";if(n==="'")return"&#x27;"}):e}append_buffer(e){var n=this._buffer+e;this._buffer=n}get_next_packet(){var e={kind:r.EOS,text:"",url:""},n=this._buffer.length;if(n==0)return e;var t=this._buffer.indexOf("\\x1B");if(t==-1)return e.kind=r.Text,e.text=this._buffer,this._buffer="",e;if(t>0)return e.kind=r.Text,e.text=this._buffer.slice(0,t),this._buffer=this._buffer.slice(t),e;if(t==0){if(n<3)return e.kind=r.Incomplete,e;var s=this._buffer.charAt(1);if(s!="["&&s!="]"&&s!="(")return e.kind=r.ESC,e.text=this._buffer.slice(0,1),this._buffer=this._buffer.slice(1),e;if(s=="["){this._csi_regex||(this._csi_regex=o(g||(g=c([\`
                        ^                           # beginning of line
                                                    #
                                                    # First attempt
                        (?:                         # legal sequence
                          \\x1B[                      # CSI
                          ([<-?]?)              # private-mode char
                          ([d;]*)                    # any digits or semicolons
                          ([ -/]?               # an intermediate modifier
                          [@-~])                # the command
                        )
                        |                           # alternate (second attempt)
                        (?:                         # illegal sequence
                          \\x1B[                      # CSI
                          [ -~]*                # anything legal
                          ([\\0-:])              # anything illegal
                        )
                    \`],[\`
                        ^                           # beginning of line
                                                    #
                                                    # First attempt
                        (?:                         # legal sequence
                          \\\\x1b\\\\[                      # CSI
                          ([\\\\x3c-\\\\x3f]?)              # private-mode char
                          ([\\\\d;]*)                    # any digits or semicolons
                          ([\\\\x20-\\\\x2f]?               # an intermediate modifier
                          [\\\\x40-\\\\x7e])                # the command
                        )
                        |                           # alternate (second attempt)
                        (?:                         # illegal sequence
                          \\\\x1b\\\\[                      # CSI
                          [\\\\x20-\\\\x7e]*                # anything legal
                          ([\\\\x00-\\\\x1f:])              # anything illegal
                        )
                    \`]))));let l=this._buffer.match(this._csi_regex);if(l===null)return e.kind=r.Incomplete,e;if(l[4])return e.kind=r.ESC,e.text=this._buffer.slice(0,1),this._buffer=this._buffer.slice(1),e;l[1]!=""||l[3]!="m"?e.kind=r.Unknown:e.kind=r.SGR,e.text=l[2];var a=l[0].length;return this._buffer=this._buffer.slice(a),e}else if(s=="]"){if(n<4)return e.kind=r.Incomplete,e;if(this._buffer.charAt(2)!="8"||this._buffer.charAt(3)!=";")return e.kind=r.ESC,e.text=this._buffer.slice(0,1),this._buffer=this._buffer.slice(1),e;this._osc_st||(this._osc_st=E(b||(b=c([\`
                        (?:                         # legal sequence
                          (\\x1B\\\\)                    # ESC                           |                           # alternate
                          (\\x07)                      # BEL (what xterm did)
                        )
                        |                           # alternate (second attempt)
                        (                           # illegal sequence
                          [\\0-]                 # anything illegal
                          |                           # alternate
                          [\\b-]                 # anything illegal
                          |                           # alternate
                          [-]                 # anything illegal
                        )
                    \`],[\`
                        (?:                         # legal sequence
                          (\\\\x1b\\\\\\\\)                    # ESC \\\\
                          |                           # alternate
                          (\\\\x07)                      # BEL (what xterm did)
                        )
                        |                           # alternate (second attempt)
                        (                           # illegal sequence
                          [\\\\x00-\\\\x06]                 # anything illegal
                          |                           # alternate
                          [\\\\x08-\\\\x1a]                 # anything illegal
                          |                           # alternate
                          [\\\\x1c-\\\\x1f]                 # anything illegal
                        )
                    \`])))),this._osc_st.lastIndex=0;{let h=this._osc_st.exec(this._buffer);if(h===null)return e.kind=r.Incomplete,e;if(h[3])return e.kind=r.ESC,e.text=this._buffer.slice(0,1),this._buffer=this._buffer.slice(1),e}{let h=this._osc_st.exec(this._buffer);if(h===null)return e.kind=r.Incomplete,e;if(h[3])return e.kind=r.ESC,e.text=this._buffer.slice(0,1),this._buffer=this._buffer.slice(1),e}this._osc_regex||(this._osc_regex=o(d||(d=c([\`
                        ^                           # beginning of line
                                                    #
                        \\x1B]8;                    # OSC Hyperlink
                        [ -:<-~]*       # params (excluding ;)
                        ;                           # end of params
                        ([!-~]{0,512})        # URL capture
                        (?:                         # ST
                          (?:\\x1B\\\\)                  # ESC                           |                           # alternate
                          (?:\\x07)                    # BEL (what xterm did)
                        )
                        ([ -~]+)              # TEXT capture
                        \\x1B]8;;                   # OSC Hyperlink End
                        (?:                         # ST
                          (?:\\x1B\\\\)                  # ESC                           |                           # alternate
                          (?:\\x07)                    # BEL (what xterm did)
                        )
                    \`],[\`
                        ^                           # beginning of line
                                                    #
                        \\\\x1b\\\\]8;                    # OSC Hyperlink
                        [\\\\x20-\\\\x3a\\\\x3c-\\\\x7e]*       # params (excluding ;)
                        ;                           # end of params
                        ([\\\\x21-\\\\x7e]{0,512})        # URL capture
                        (?:                         # ST
                          (?:\\\\x1b\\\\\\\\)                  # ESC \\\\
                          |                           # alternate
                          (?:\\\\x07)                    # BEL (what xterm did)
                        )
                        ([\\\\x20-\\\\x7e]+)              # TEXT capture
                        \\\\x1b\\\\]8;;                   # OSC Hyperlink End
                        (?:                         # ST
                          (?:\\\\x1b\\\\\\\\)                  # ESC \\\\
                          |                           # alternate
                          (?:\\\\x07)                    # BEL (what xterm did)
                        )
                    \`]))));let l=this._buffer.match(this._osc_regex);if(l===null)return e.kind=r.ESC,e.text=this._buffer.slice(0,1),this._buffer=this._buffer.slice(1),e;e.kind=r.OSCURL,e.url=l[1],e.text=l[2];var a=l[0].length;return this._buffer=this._buffer.slice(a),e}else if(s=="(")return e.kind=r.Unknown,this._buffer=this._buffer.slice(3),e}}ansi_to_html(e){this.append_buffer(e);for(var n=[];;){var t=this.get_next_packet();if(t.kind==r.EOS||t.kind==r.Incomplete)break;t.kind==r.ESC||t.kind==r.Unknown||(t.kind==r.Text?n.push(this.transform_to_html(this.with_state(t))):t.kind==r.SGR?this.process_ansi(t):t.kind==r.OSCURL&&n.push(this.process_hyperlink(t)))}return n.join("")}with_state(e){return{bold:this.bold,faint:this.faint,italic:this.italic,underline:this.underline,fg:this.fg,bg:this.bg,text:e.text}}process_ansi(e){let n=e.text.split(";");for(;n.length>0;){let t=n.shift(),s=parseInt(t,10);if(isNaN(s)||s===0)this.fg=null,this.bg=null,this.bold=!1,this.faint=!1,this.italic=!1,this.underline=!1;else if(s===1)this.bold=!0;else if(s===2)this.faint=!0;else if(s===3)this.italic=!0;else if(s===4)this.underline=!0;else if(s===21)this.bold=!1;else if(s===22)this.faint=!1,this.bold=!1;else if(s===23)this.italic=!1;else if(s===24)this.underline=!1;else if(s===39)this.fg=null;else if(s===49)this.bg=null;else if(s>=30&&s<38)this.fg=this.ansi_colors[0][s-30];else if(s>=40&&s<48)this.bg=this.ansi_colors[0][s-40];else if(s>=90&&s<98)this.fg=this.ansi_colors[1][s-90];else if(s>=100&&s<108)this.bg=this.ansi_colors[1][s-100];else if((s===38||s===48)&&n.length>0){let a=s===38,l=n.shift();if(l==="5"&&n.length>0){let f=parseInt(n.shift(),10);f>=0&&f<=255&&(a?this.fg=this.palette_256[f]:this.bg=this.palette_256[f])}if(l==="2"&&n.length>2){let f=parseInt(n.shift(),10),h=parseInt(n.shift(),10),_=parseInt(n.shift(),10);if(f>=0&&f<=255&&h>=0&&h<=255&&_>=0&&_<=255){let y={rgb:[f,h,_],class_name:"truecolor"};a?this.fg=y:this.bg=y}}}}}transform_to_html(e){let n=e.text;if(n.length===0||(n=this.escape_txt_for_html(n),!e.bold&&!e.italic&&!e.faint&&!e.underline&&e.fg===null&&e.bg===null))return n;let t=[],s=[],a=e.fg,l=e.bg;e.bold&&t.push(this._boldStyle),e.faint&&t.push(this._faintStyle),e.italic&&t.push(this._italicStyle),e.underline&&t.push(this._underlineStyle),this._use_classes?(a&&(a.class_name!=="truecolor"?s.push(\`\${a.class_name}-fg\`):t.push(\`color:rgb(\${a.rgb.join(",")})\`)),l&&(l.class_name!=="truecolor"?s.push(\`\${l.class_name}-bg\`):t.push(\`background-color:rgb(\${l.rgb.join(",")})\`))):(a&&t.push(\`color:rgb(\${a.rgb.join(",")})\`),l&&t.push(\`background-color:rgb(\${l.rgb})\`));let f="",h="";return s.length&&(f=\` class="\${s.join(" ")}"\`),t.length&&(h=\` style="\${t.join(";")}"\`),\`<span\${h}\${f}>\${n}</span>\`}process_hyperlink(e){let n=e.url.split(":");return n.length<1||!this._url_allowlist[n[0]]?"":\`<a href="\${this.escape_txt_for_html(e.url)}">\${this.escape_txt_for_html(e.text)}</a>\`}}function o(i,...e){let n=i.raw[0],t=/^\\s+|\\s+\\n|\\s*#[\\s\\S]*?\\n|\\n/gm,s=n.replace(t,"");return new RegExp(s)}function E(i,...e){let n=i.raw[0],t=/^\\s+|\\s+\\n|\\s*#[\\s\\S]*?\\n|\\n/gm,s=n.replace(t,"");return new RegExp(s,"g")}var g,b,d;const p=new TextDecoder("utf-8"),m=new w;m.ansi_to_html("\\x1B[m");function C(i){if(i.indexOf("\\r")!==-1){const e=i.split("\\r");let n=e.shift();return e.map(t=>{n=t+n.substring(t.length)}),n}return i}function k(i,e){return i.trim().indexOf("[DAEMON]")===0?\`<span class="daemon-marker" data-name="\${e}"></span>\`+i.substring(8):i}function x(i,e){return k(C(i),e)}function O(i,e){const n=atob(e),t=new Uint8Array(n.length+i.length);for(let l=0;l<t.length;l++)l<i.length?t[l]=i[l]:t[l]=n.charCodeAt(l-i.length);let s=p.decode(t),a=new Uint8Array(0);if(s.slice(-1)==="�"){for(let l=0;l<3;l++)if(p.decode(t.slice(l-3))==="�"){s=s.slice(0,-1),a=t.slice(l-3);break}}return{decoded:s,incomplete:a}}let u=null,S=new Uint8Array(0);onmessage=function(i){if(i.data.logs.length===0)return;const{decoded:e,incomplete:n}=O(S,Array.isArray(i.data.logs)?i.data.logs.join(""):i.data.logs);S=n;let t=e.replaceAll(\`\\r
\`,\`
\`);const s=t.endsWith(\`
\`);t=t.split(\`
\`),s&&t.pop();const a=[];t.map((l,f)=>{l=m.ansi_to_html(l),u&&(l=u.line+l),u?(a.push({op:"update",content:x(l,i.data.panelName)}),t.length-1===f&&!s?u={line:l}:u=null):(a.push({op:"append",content:x(l,i.data.panelName)}),t.length-1===f&&!s&&(u={line:l}))}),postMessage(a)}})();
//# sourceMappingURL=consoleWorker-C0SFFFyf.js.map
`,I=typeof self<"u"&&self.Blob&&new Blob(["(self.URL || self.webkitURL).revokeObjectURL(self.location.href);",B],{type:"text/javascript;charset=utf-8"});function G(a){let o;try{if(o=I&&(self.URL||self.webkitURL).createObjectURL(I),!o)throw"";const u=new Worker(o,{name:a?.name});return u.addEventListener("error",()=>{(self.URL||self.webkitURL).revokeObjectURL(o)}),u}catch{return new Worker("data:text/javascript;charset=utf-8,"+encodeURIComponent(B),{name:a?.name})}}const K=["textContent"],P={key:1,dir:"ltr",class:"console-wrapper"},X={key:2,dir:"ltr",class:"command"},Q={__name:"Console",props:{server:{type:Object,required:!0}},setup(a){const o=new G;let u=null;const{t:S}=q(),$=M("config").branding.name,l=f(""),c=f(null);let g=0;const r=a;let _=null,p=null;N(async()=>{o.addEventListener("message",j),_=r.server.on("console",b),b(await r.server.getConsole()),p=r.server.startTask(async()=>{r.server.needsPolling()&&r.server.hasScope("server.console")&&b(await r.server.getConsole(g))},5e3)}),V(()=>{_&&_(),p&&r.server.stopTask(p),k()});function b(h){"epoch"in h?g=h.epoch:g=Date.now(),o.postMessage({...h,panelName:$})}function j(h){const e=[];if(h.data.map(s=>{if(s.op==="update"&&u)u.innerHTML=s.content;else{const i=document.createElement("div");i.innerHTML=s.content,e.push(i),u=i}}),e+c.value.children.length>1200){let s=c.value.children.concat(e);s=s.slice(s.length-1e3,s.length),c.value.replaceChildren(s)}else c.value.append(...e)}function k(){c.value&&c.value.replaceChildren([])}const n=f([]),t=f(-1),m=f("");function w(){t.value!==-1&&n.value.splice(t.value,1),(n.value.length===0||n.value[n.value.length-1]!==l.value)&&n.value.push(l.value),t.value=-1,m.value="",n.value.length>100&&n.value.splice(0,1),r.server.sendCommand(l.value),l.value=""}function T(){if(t.value===-1&&n.value.length>0)t.value=n.value.length-1,m.value=l.value;else if(t.value>0)t.value--;else return;l.value=n.value[t.value]}function A(){t.value!==-1&&(t.value++,t.value>=n.value.length?(t.value=-1,l.value=m.value):l.value=n.value[t.value])}return(h,e)=>{const s=W("hotkey");return d(),x("div",null,[C("h2",{textContent:D(U(S)("servers.Console"))},null,8,K),a.server.hasScope("server.console")?E((d(),H(L,{key:0,name:"clear-console",onClick:e[0]||(e[0]=i=>k())},null,512)),[[s,"c x"]]):v("",!0),a.server.hasScope("server.console")?(d(),x("div",P,[C("div",{ref_key:"console",ref:c,class:"console"},null,512)])):v("",!0),a.server.hasScope("server.console.send")?(d(),x("div",X,[E(O(F,{modelValue:l.value,"onUpdate:modelValue":e[1]||(e[1]=i=>l.value=i),label:U(S)("servers.Command"),onKeyup:e[2]||(e[2]=y(i=>w(),["enter"])),onKeydown:[e[3]||(e[3]=y(R(i=>T(),["prevent"]),["up"])),e[4]||(e[4]=y(R(i=>A(),["prevent"]),["down"]))]},null,8,["modelValue","label"]),[[s,"c c"]]),O(L,{name:"send",onClick:e[5]||(e[5]=i=>w())})])):v("",!0)])}}};export{Q as default};
//# sourceMappingURL=Console-BhX6_BZS.js.map
