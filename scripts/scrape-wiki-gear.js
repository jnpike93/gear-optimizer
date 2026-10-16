/**
 * OSRS Wiki Live Gear Scraper
 * Extracts live strategy gear tables directly from Old School RuneScape Wiki MediaWiki API,
 * maps item names to IDs using the official Wiki Item Mapping API,
 * and compiles minified, ranked static gear datasets for the RuneLite Gear Optimizer.
 */

const fs = require('fs');
const path = require('path');

const OUTPUT_RESOURCES = path.join(__dirname, '../src/main/resources/gear_database.json');
const OUTPUT_DATA = path.join(__dirname, '../data/gear_database.json');

const ALIAS_MAP = {
  'anti-dragon shield': [1540],
  'antifire shield': [1540],
  'dragonfire shield': [11283, 11284],
  'dragonfire ward': [22002, 22003],
  'salve amulet(ei)': [12018],
  'salve amulet (ei)': [12018],
  'salve amulet (e)': [10588],
  'salve amulet': [4081],
  'infernal cape': [21295],
  'fire cape': [6570],
  'mythical cape': [22114],
  'imbued saradomin cape': [21791],
  'saradomin cape': [2412],
  'imbued zamorak cape': [21795],
  'zamorak cape': [2414],
  'imbued guthix cape': [21793],
  'guthix cape': [2413],
  'slayer helmet (i)': [11865, 11864],
  'black mask (i)': [11784, 8921],
  'ardougne cloak': [13124, 13123, 13122, 13121],
  'ardougne cloak 4': [13124],
  "dizana's quiver": [28951, 28952, 28953],
  "ava's assembler": [22109],
  "ava's accumulator": [10499],
  'toxic blowpipe': [12926],
  'trident of the swamp': [12899],
  'trident of the seas': [11905],
  'bow of faerdhinen': [25865],
  'bow of faerdhinen (c)': [25865],
  "tumeken's shadow": [27275],
  'scythe of vitur': [22325, 22486],
  "osmumten's fang": [26219],
  'dragon hunter lance': [22978],
  'dragon hunter crossbow': [21902],
  'twisted bow': [20997],
  'avernic defender': [22322],
  'dragon defender': [12954],
  'rune defender': [8850],
  'ferocious gloves': [22981],
  'barrows gloves': [7462],
  'primordial boots': [13239],
  'dragon boots': [11840],
  'pegasian boots': [13237],
  'ranger boots': [2577],
  'eternal boots': [13235],
  'infinity boots': [6920],
  'ultor ring': [28307],
  'venator ring': [28310],
  'magus ring': [28313],
  'bellator ring': [28316],
  'berserker ring (i)': [11773],
  'archers ring (i)': [11771],
  'seers ring (i)': [11770],
  'ring of suffering (i)': [19550],
  'ring of suffering (ri)': [19550],
  'lightbearer': [25975],
  'barrows platelegs': [4751, 4970, 4971, 4972, 4973, 4722, 4898, 4899, 4900, 4901, 4759, 4994, 4995, 4996, 4997, 4730, 4922, 4923, 4924, 4925],
  'barrows legs': [4751, 4970, 4971, 4972, 4973, 4722, 4898, 4899, 4900, 4901, 4759, 4994, 4995, 4996, 4997, 4730, 4922, 4923, 4924, 4925],
  'barrows platebody': [4749, 4964, 4965, 4966, 4967, 4720, 4892, 4893, 4894, 4895, 4728, 4916, 4917, 4918, 4919, 4757, 4988, 4989, 4990, 4991],
  'barrows body': [4749, 4964, 4965, 4966, 4967, 4720, 4892, 4893, 4894, 4895, 4728, 4916, 4917, 4918, 4919, 4757, 4988, 4989, 4990, 4991],
  'barrows helm': [4745, 4952, 4953, 4954, 4955, 4716, 4880, 4881, 4882, 4883, 4724, 4904, 4905, 4906, 4907, 4753, 4976, 4977, 4978, 4979],
  'blood moon helm': [29028, 29047, 29073, 29848],
  'blood moon chestplate': [29022, 29043, 29067, 29846],
  'blood moon tassets': [29025, 29045, 29070, 29847],
  'blue moon helm': [29034, 29053, 29079],
  'blue moon chestplate': [29031, 29051, 29076],
  'blue moon tassets': [29037, 29055, 29082],
  'eclipse moon helm': [29019, 29043, 29064],
  'eclipse moon chestplate': [29013, 29039, 29058],
  'eclipse moon tassets': [29016, 29041, 29061],
  'justiciar faceguard': [22326],
  'justiciar chestguard': [22327],
  'justiciar legguards': [22328],
  'oathplate helm': [29929],
  'oathplate chest': [29932],
  'oathplate legs': [29935],
  'torva full helm': [26382],
  'torva platebody': [26384],
  'torva platelegs': [26386],
  'masori mask (f)': [27226],
  'masori body (f)': [27229],
  'masori chaps (f)': [27232],
  'masori mask': [27351],
  'masori body': [27354],
  'masori chaps': [27357],
  'ancestral hat': [21018],
  'ancestral robe top': [21021],
  'ancestral robe bottom': [21024],
  'virtus mask': [26231],
  'virtus robe top': [26233],
  'virtus robe bottom': [26235],
  "elidinis' ward (f)": [27253],
  "elidinis' ward": [27251],
  'fighter torso': [10551],
  'bandos chestplate': [11832],
  'bandos tassets': [11834],
  'neitiznot faceguard': [23230],
  'helm of neitiznot': [10828],
  'serpentine helm': [12931],
  "torag's helm": [4745, 4952, 4953, 4954, 4955, 4956, 23637],
  "torag's platebody": [4749, 4964, 4965, 4966, 4967, 4968],
  "torag's platelegs": [4751, 4970, 4971, 4972, 4973, 4974, 23634],
  "dharok's helm": [4716, 4880, 4881, 4882, 4883, 4884, 23639],
  "dharok's platebody": [4720, 4892, 4893, 4894, 4895, 4896, 25515],
  "dharok's platelegs": [4722, 4898, 4899, 4900, 4901, 4902, 23633],
  "guthan's helm": [4724, 4904, 4905, 4906, 4907, 4908, 23638],
  "guthan's platebody": [4728, 4916, 4917, 4918, 4919, 4920],
  "guthan's chainskirt": [4730, 4922, 4923, 4924, 4925, 4926],
  "verac's helm": [4753, 4976, 4977, 4978, 4979, 4980, 23636],
  "verac's brassard": [4757, 4988, 4989, 4990, 4991, 4992, 27190],
  "verac's plateskirt": [4759, 4994, 4995, 4996, 4997, 4998, 23635],
  "guthan's platebody": [4728],
  "guthan's chainskirt": [4730],
  "verac's helm": [4753],
  "verac's brassard": [4757],
  "verac's plateskirt": [4759],
  "ahrim's hood": [4708],
  "ahrim's robetop": [4712],
  "ahrim's robeskirt": [4714],
  "karil's coif": [4732],
  "karil's leathertop": [4736],
  "karil's leatherskirt": [4740],
  'crystal helm': [23971],
  'crystal body': [23975],
  'crystal legs': [23979],
  'void ranger helm': [11664],
  'void melee helm': [11665],
  'void mage helm': [11663],
  'elite void top': [13072],
  'elite void robe': [13073],
  'void knight top': [8839],
  'void knight robe': [8840],
  'void knight gloves': [8842],
  "rada's blessing 4": [22947],
  "rada's blessing 3": [22945],
  'god blessing': [20220, 20223, 20226, 20229, 20232, 20235],
  'peaceful blessing': [20220],
  'holy blessing': [20223]
};

function extractTemplates(text, templateName) {
  const marker = '{{' + templateName;
  const lower = text.toLowerCase();
  let idx = lower.indexOf(marker.toLowerCase());
  const blocks = [];
  while (idx !== -1) {
    let braceCount = 0;
    let endIdx = idx;
    for (let i = idx; i < text.length - 1; i++) {
      if (text[i] === '{' && text[i+1] === '{') {
        braceCount++;
        i++;
      } else if (text[i] === '}' && text[i+1] === '}') {
        braceCount--;
        i++;
        if (braceCount === 0) {
          endIdx = i + 1;
          break;
        }
      }
    }
    if (braceCount === 0) {
      blocks.push(text.substring(idx + marker.length, endIdx - 2));
    }
    idx = lower.indexOf(marker.toLowerCase(), endIdx);
  }
  return blocks;
}

async function fetchWikiMapping() {
  console.log('[Wiki Scraper] Fetching live OSRS Wiki item mappings...');
  const resp = await fetch('https://prices.runescape.wiki/api/v1/osrs/mapping', {
    headers: { 'User-Agent': 'RuneLiteGearOptimizer/1.0' }
  });
  if (!resp.ok) {
    throw new Error(`Failed to fetch item mappings: ${resp.status}`);
  }
  const mapping = await resp.json();
  const nameToId = new Map();
  for (const item of mapping) {
    nameToId.set(item.name.toLowerCase().trim(), item.id);
  }
  console.log(`[Wiki Scraper] Loaded ${nameToId.size} item mappings.`);
  return nameToId;
}

function resolveItemIds(rawName, nameToId) {
  if (!rawName) return [];
  const clean = rawName.toLowerCase().replace(/\[\[|\]\]/g, '').trim();

  // Check alias map first
  if (ALIAS_MAP[clean]) {
    return ALIAS_MAP[clean];
  }

  // Exact match in wiki mapping
  if (nameToId.has(clean)) {
    return [nameToId.get(clean)];
  }

  // Handle (i), (ri), (e), (f) or other qualifiers
  const stripped = clean.replace(/\s*\([^)]*\)/g, '').trim();
  if (ALIAS_MAP[stripped]) {
    return ALIAS_MAP[stripped];
  }
  if (nameToId.has(stripped)) {
    return [nameToId.get(stripped)];
  }

  return [];
}

function parseSlotItems(lineValue, nameToId) {
  const ids = [];
  const tokens = lineValue.split(/\/|<br\s*\/?>|<\/br>|,|>/i);

  for (const token of tokens) {
    let itemToken = token.replace(/\{\{efn[^}]*\}\}/gi, '').trim();

    // Check for {{plink|Item|pic=RealItem}}
    const plinkMatch = itemToken.match(/\{\{plink\|([^|}]+)(?:\|pic=([^|}]+))?(?:\|txt=([^|}]+))?[^}]*\}\}/i);
    if (plinkMatch) {
      if (plinkMatch[1]) {
        ids.push(...resolveItemIds(plinkMatch[1], nameToId));
      }
      if (plinkMatch[2]) {
        ids.push(...resolveItemIds(plinkMatch[2], nameToId));
      }
      continue;
    }

    // Check for [[Item]]
    const wikiLinkMatch = itemToken.match(/\[\[([^|\]]+)(?:\|[^\]]+)?\]\]/);
    if (wikiLinkMatch) {
      const resolved = resolveItemIds(wikiLinkMatch[1], nameToId);
      ids.push(...resolved);
      continue;
    }

    // Check raw name
    const rawClean = itemToken.replace(/[{}[\]]/g, '').trim();
    if (rawClean.length > 1) {
      const resolved = resolveItemIds(rawClean, nameToId);
      ids.push(...resolved);
    }
  }

  return [...new Set(ids)];
}

async function scrapeWikiGear() {
  console.log('[Wiki Scraper] Starting live Wiki gear synchronization...');
  const nameToId = await fetchWikiMapping();

  let existingDb = { activities: {} };
  if (fs.existsSync(OUTPUT_RESOURCES)) {
    try {
      existingDb = JSON.parse(fs.readFileSync(OUTPUT_RESOURCES, 'utf8'));
    } catch (e) {}
  }

  const activities = existingDb.activities || {};

  const pagesToScrape = [
    { page: 'Vorkath/Strategies', bossName: 'Vorkath', category: 'Bossing' },
    { page: 'Zulrah/Strategies', bossName: 'Zulrah', category: 'Bossing' },
    { page: 'Phantom_Muspah/Strategies', bossName: 'Phantom Muspah', category: 'Bossing' },
    { page: 'Alchemical_Hydra/Strategies', bossName: 'Alchemical Hydra', category: 'Slayer' },
    { page: 'General_Graardor/Strategies', bossName: 'General Graardor', category: 'Bossing' },
    { page: 'Tombs_of_Amascut/Strategies', bossName: 'Tombs of Amascut', category: 'Raids' },
    { page: 'Chambers_of_Xeric/Strategies', bossName: 'Chambers of Xeric', category: 'Raids' },
    { page: 'Vardorvis/Strategies', bossName: 'Vardorvis', category: 'Bossing' },
    { page: 'Duke_Sucellus/Strategies', bossName: 'Duke Sucellus', category: 'Bossing' },
    { page: 'The_Whisperer/Strategies', bossName: 'The Whisperer', category: 'Bossing' },
    { page: 'The_Leviathan/Strategies', bossName: 'The Leviathan', category: 'Bossing' }
  ];

  for (const item of pagesToScrape) {
    try {
      console.log(`[Wiki Scraper] Scraping strategy page: ${item.page}...`);
      const url = `https://oldschool.runescape.wiki/api.php?action=parse&page=${encodeURIComponent(item.page)}&prop=wikitext&format=json`;
      const resp = await fetch(url, { headers: { 'User-Agent': 'RuneLiteGearOptimizer/1.0' } });
      if (!resp.ok) continue;

      const data = await resp.json();
      if (!data.parse || !data.parse.wikitext) continue;

      const wikitext = data.parse.wikitext['*'];
      const gearBlocks = extractTemplates(wikitext, 'Recommended equipment');

      for (let b = 0; b < gearBlocks.length; b++) {
        const block = gearBlocks[b];
        const lines = block.split('\n');

        let style = 'Melee';
        const slots = {};

        for (const line of lines) {
          const match = line.match(/^\|([a-zA-Z0-9_]+)\s*=\s*(.*)$/);
          if (!match) continue;

          const param = match[1].toLowerCase().trim();
          const val = match[2].trim();

          if (param === 'style') {
            if (/range/i.test(val)) style = 'Ranged';
            else if (/magic|mage/i.test(val)) style = 'Magic';
            else style = 'Melee';
            continue;
          }

          const slotMatch = param.match(/^([a-zA-Z]+)(\d*)$/);
          if (!slotMatch) continue;

          let slotName = slotMatch[1];
          if (slotName === 'feet') slotName = 'boots';

          const slotIds = parseSlotItems(val, nameToId);
          if (slotIds.length > 0) {
            if (!slots[slotName]) slots[slotName] = [];
            for (const id of slotIds) {
              if (!slots[slotName].includes(id)) {
                slots[slotName].push(id);
              }
            }
          }
        }

        if (Object.keys(slots).length >= 4) {
          // Boss-specific essential protection gear
          if (item.bossName === 'Vorkath') {
            if (style === 'Melee') {
              if (!slots.shield) slots.shield = [];
              if (!slots.shield.includes(11283)) slots.shield.push(11283, 11284); // Dragonfire shield
              if (!slots.shield.includes(1540)) slots.shield.push(1540);          // Anti-dragon / antifire shield
              if (!slots.head) slots.head = [];
              if (!slots.head.includes(29028)) slots.head.push(29028);            // Blood moon helm
              if (!slots.body) slots.body = [];
              if (!slots.body.includes(29022)) slots.body.push(29022);            // Blood moon chestplate
              if (!slots.legs) slots.legs = [];
              if (!slots.legs.includes(29025)) slots.legs.push(29025);            // Blood moon tassets
            } else if (style === 'Ranged') {
              if (!slots.shield) slots.shield = [];
              if (!slots.shield.includes(1540)) slots.shield.push(1540);          // Anti-dragon / antifire shield
              if (!slots.shield.includes(11283)) slots.shield.push(11283, 11284); // Dragonfire shield
            }
          }

          const activityId = `${item.bossName.toLowerCase().replace(/[^a-z0-9]/g, '_')}_${style.toLowerCase()}`;
          const activityName = `${item.bossName} (${style})`;

          activities[activityId] = {
            id: activityId,
            name: activityName,
            category: item.category,
            style: style,
            slots: slots,
            inventory: activities[activityId]?.inventory || [
              { id: 2434, qty: 3, optional: false },
              { id: 391, qty: 8, optional: false }
            ]
          };

          console.log(`[Wiki Scraper] Successfully parsed: ${activityName} with ${Object.keys(slots).length} slots.`);
        }
      }
    } catch (err) {
      console.warn(`[Wiki Scraper] Warning while processing ${item.page}:`, err.message);
    }
  }

  const finalDb = {
    version: parseInt(new Date().toISOString().slice(0, 10).replace(/-/g, ''), 10),
    last_updated: new Date().toISOString(),
    activities: activities
  };

  const jsonStr = JSON.stringify(finalDb, null, 2);

  fs.mkdirSync(path.dirname(OUTPUT_RESOURCES), { recursive: true });
  fs.writeFileSync(OUTPUT_RESOURCES, jsonStr);

  fs.mkdirSync(path.dirname(OUTPUT_DATA), { recursive: true });
  fs.writeFileSync(OUTPUT_DATA, jsonStr);

  console.log(`[Wiki Scraper] Synchronization complete! Saved ${Object.keys(activities).length} activities to ${OUTPUT_RESOURCES}`);
}

scrapeWikiGear().catch(err => {
  console.error('[Wiki Scraper] Fatal error:', err);
  process.exit(1);
});
