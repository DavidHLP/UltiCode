-- thesis-filter.lua: 洛阳师范学院本科毕业论文格式清洗
-- 功能：
--   1. 移除手动"目录"节（由 pandoc --toc 自动生成）
--   2. 第一个 H1 → ThesisTitle（论文大标题，不入TOC）
--   3. ## 摘要 → AbstractTitle（不入TOC），内容 → AbstractBody
--   4. ## Abstract → EnAbstractTitle（不入TOC），内容 → EnAbstractBody
--   5. 设置语言为 zh-CN

local in_toc_section = false
local first_h1_done = false
local in_abstract = false
local in_en_abstract = false

function Header(el)
  local text = pandoc.utils.stringify(el.content)

  -- 0. 优先退出摘要区域（遇到非自身标题的 H1/H2）
  if in_abstract and el.level <= 2 and text ~= "摘要" then
    in_abstract = false
  end
  if in_en_abstract and el.level <= 2 and text ~= "Abstract" then
    in_en_abstract = false
  end

  -- 1. 检测 ## 目录，开始跳过
  if el.level == 2 and text == "目录" then
    in_toc_section = true
    return {}
  end

  if in_toc_section then
    if el.level == 1 then
      in_toc_section = false
      return el
    end
    return {}
  end

  -- 2. 第一个 H1 → ThesisTitle（论文大标题）
  if el.level == 1 and not first_h1_done then
    first_h1_done = true
    return pandoc.Div(
      { pandoc.Para { pandoc.Str(text) } },
      pandoc.Attr("", {}, { ["custom-style"] = "ThesisTitle" })
    )
  end

  -- 3. ## 摘要 → AbstractTitle
  if el.level == 2 and text == "摘要" then
    in_abstract = true
    return pandoc.Div(
      { pandoc.Para { pandoc.Str(text) } },
      pandoc.Attr("", {}, { ["custom-style"] = "AbstractTitle" })
    )
  end

  -- 4. ## Abstract → EnAbstractTitle
  if el.level == 2 and text == "Abstract" then
    in_en_abstract = true
    return pandoc.Div(
      { pandoc.Para { pandoc.Str(text) } },
      pandoc.Attr("", {}, { ["custom-style"] = "EnAbstractTitle" })
    )
  end

  return el
end

function Para(el)
  if in_toc_section then return {} end

  if in_abstract then
    return pandoc.Div(
      { el },
      pandoc.Attr("", {}, { ["custom-style"] = "AbstractBody" })
    )
  end

  if in_en_abstract then
    return pandoc.Div(
      { el },
      pandoc.Attr("", {}, { ["custom-style"] = "EnAbstractBody" })
    )
  end

  return el
end

-- 目录节内的其他块级元素全部跳过
function HorizontalRule(el)
  if in_toc_section then return {} end
  return el
end

function BulletList(el)
  if in_toc_section then return {} end
  return el
end

function OrderedList(el)
  if in_toc_section then return {} end
  return el
end

function BlockQuote(el)
  if in_toc_section then return {} end
  return el
end

function Meta(meta)
  meta.lang = pandoc.MetaInlines { pandoc.Str("zh-CN") }
  return meta
end
