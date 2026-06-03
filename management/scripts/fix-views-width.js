const fs = require('fs');
const path = require('path');

const walk = (dir) => {
  let results = [];
  const list = fs.readdirSync(dir);
  list.forEach((file) => {
    file = path.join(dir, file);
    const stat = fs.statSync(file);
    if (stat && stat.isDirectory()) {
      results = results.concat(walk(file));
    } else {
      if (file.endsWith('.vue')) results.push(file);
    }
  });
  return results;
};

const viewsDir = path.join(__dirname, '../src/views');
const vueFiles = walk(viewsDir);

vueFiles.forEach(file => {
  let content = fs.readFileSync(file, 'utf8');
  let updated = false;
  
  if (content.includes('class="relative flex flex-col gap-0 overflow-auto"')) {
    content = content.replace('class="relative flex flex-col gap-0 overflow-auto"', 'class="relative flex flex-col gap-0 w-full min-w-0"');
    updated = true;
  }
  
  if (content.includes('class="relative flex flex-col gap-4 overflow-auto p-6"')) {
    content = content.replace('class="relative flex flex-col gap-4 overflow-auto p-6"', 'class="relative flex flex-col gap-4 w-full min-w-0 p-6"');
    updated = true;
  }

  if (updated) {
    fs.writeFileSync(file, content, 'utf8');
    console.log('Fixed wrapper width for:', path.relative(viewsDir, file));
  }
});
