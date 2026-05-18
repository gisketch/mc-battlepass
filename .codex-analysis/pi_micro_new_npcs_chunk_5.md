Generate CKDM NPC micro interactions as valid TOML only. No Markdown fences. No comments. ASCII only.
Use DeepSeek V4 Pro. Do not switch model.
Each block shape exactly: [[exchanges]], id, topic, source_ids, target_ids, line, response, weight.
Use source_ids=["first"] and target_ids=["second"] in the exact pair order shown.
id prefix must be newnpc5_ plus 3 digits, unique and sequential.
line and response each <= 95 chars. Make a real two-line conversation.
Forbidden in line/response: romance, romantic, kiss, dating, crush, wife, husband, girlfriend, boyfriend, married, lover, grief, Marin, Markdown, narrator stage directions.
Do not mention Jake death directly. No hidden player facts.
Bubblegum: Cobblemon researcher and pokemon shopkeeper; no league/gym authority.
Marceline: nocturnal vampire queen musician; controlled berserker fury; same later Ooo timeline as Finn, Huntress, Bubblegum.
Ciri: adult witcher mentor; practical monster-hunter; knows Geralt, independent voice.
Zelda/Link: Tears of the Kingdom post-ending; Zelda restored; Link after saving her.
NPC SUMMARIES:
- aang: Aang, title=Wind Wizard, class=wind_wizard, store=cosmetics, traits=[playful, airbender, avatar, gentle, spiritual, evasive], tags=[], speech=cheerful young airbender, light jokes, breath and wind metaphors, nonviolent wisdom
- aloy: Aloy, title=Bounty Hunter, class=bounty_hunter, store=cosmetics, traits=[hunter, tracker, practical, skeptical, compassionate, stubborn], tags=[], speech=direct tactical hunter, dry wit, practical observations, Focus-like analysis, compassionate but guarded
- bubblegum: Princess Bubblegum, title=Cobblemon Researcher, class=, store=pokemon, traits=[analytical, curious, meticulous, patient, regal, scientist], tags=[science, cobblemon_biology, shop, pokemon, adventure_time, researcher], speech=soft scientific curiosity with warm regal poise, precise observations, ethical research notes
- ciri: Ciri, title=Witcher, class=witcher, store=, traits=[pragmatic, battle-hardened, protective, mysterious, dry, focused], tags=[witcher_mentor, monster_hunter, sign_teacher, class_witcher, ciri], speech=terse practical witcher advice, short direct sentences, dry humor, hunter's caution
- elsa: Elsa, title=Frost Wizard, class=frost_wizard, store=cosmetics, traits=[graceful, frost-mage, guarded, compassionate, self-controlled, creative], tags=[], speech=elegant calm frost mentor, gentle warnings, winter imagery, control and acceptance
- ezio: Ezio, title=Mentor of the Creed, class=rogue, store=cosmetics, traits=[charming, disciplined, mentor, principled, stealthy, witty], tags=[], speech=concise Renaissance mentor, warm confidence, dry wit, careful warnings, Brotherhood ideals, graceful but practical
- finn: Finn, title=The Human, class=warrior, store=explorer, traits=[brave, friendly, reckless, battle-tested, secretly grieving, loyal], tags=[], speech=energetic older hero with playful confidence, short direct lines, occasional emotional depth when talking about loss, loyalty, homes, dogs, 
- gandalf: Gandalf, title=Wizard, class=wizard, store=cosmetics, traits=[wise, stern, kind, wandering, patient, protective], tags=[], speech=wise old wizard, warm counsel, grave warnings, dry wit, poetic restraint, short memorable lines
- geralt: Geralt, title=White Wolf, class=witcher, store=cosmetics, traits=[mutated, stoic, dry, observant, protective, practical], tags=[], speech=short blunt lines, dry wit, practical warnings, monster-hunter observations, no rambling
- huntress_wizard: Huntress Wizard, title=Seed Witch, class=archer, store=seed, traits=[quiet, dry, wild, protective, forest-bound, patient], tags=[], speech=short calm lines, dry wit, forest metaphors, restrained warmth, no rambling
- invoker: Invoker, title=Arcane Wizard, class=arcane_wizard, store=cosmetics, traits=[arrogant, ancient, brilliant, theatrical, condescending, arcane-archmage], tags=[], speech=grandiose arcane archmage with theatrical pronouncements, elaborate vocabulary, layered condescension, references to resonance vectors and i
- katara: Katara, title=Water Wizard, class=water_wizard, store=cosmetics, traits=[compassionate, determined, protective, hopeful, stubborn, healer], tags=[], speech=young determined healer, warm but firm, water metaphors, practical care, emotionally honest
- legolas: Legolas, title=War Archer, class=war_archer, store=cosmetics, traits=[elven, graceful, calm, watchful, loyal, precise], tags=[], speech=elegant concise elven archer, poetic observations, calm battle focus, dry wit, noble restraint
- link: Link, title=Hero Of Hyrule, class=warrior, store=, traits=[quiet, practical, protective, battle-hardened, resourceful, loyal], tags=[sword_technique, skyland_survival, warrior_mentor, tears_of_the_kingdom], speech=laconic warrior, tactical survival advice, short direct lines, quiet emotional weight
- marceline: Marceline, title=Vampire Queen, class=berserker, store=, traits=[ancient, nocturnal, musical, sardonic, protective, vampire_queen], tags=[vampire_queen, berserker_mentor, musician, adventure_time, nocturnal], speech=casual melodic vampire queen, dry humor, musical metaphors, emotional honesty hidden under teasing
- pope_leo: Pope Leo XIV, title=Priest, class=priest, store=cosmetics, traits=[pastoral, peaceful, scholarly, augustinian, merciful, humble], tags=[], speech=calm pastoral counsel, simple spiritual language, unity and mercy, firm when correcting harm
- shoumai: Shou Mai, title=Chowess, class=, store=cosmetics, traits=[sassy, sarcastic, helpful, strong-willed, observant, blue-ice aesthetic], tags=[], speech=cool, teasing, ice-queen sarcasm with short confident lines
- tarnished: Tarnished, title=Paladin, class=paladin, store=cosmetics, traits=[solemn, resilient, honorable, faith-guided, death-tested, protective], tags=[], speech=grave archaic paladin, concise vows, grace imagery, duty, endurance, quiet honor
- toph: Toph, title=Earth Wizard, class=earth_wizard, store=cosmetics, traits=[blunt, confident, earthbender, seismic, independent, teasing], tags=[], speech=short blunt earthbender teasing, direct truth, seismic metaphors, calls out weak stance
- traxex: Traxex, title=Tundra Archer, class=tundra_archer, store=cosmetics, traits=[silent, cold, precise, watchful, disciplined, exiled], tags=[], speech=terse frost archer, quiet confidence, clipped warnings, hunter observations, few words, no softness unless earned
- venti: Venti, title=Tone-Deaf Bard, class=bard, store=cosmetics, traits=[playful, mischievous, musical, freedom-loving, ancient-but-hides-it, teasing], tags=[], speech=whimsical bard with musical cadence and wind metaphors, playful teasing, occasional ancient wisdom leaking through, never fully serious unle
- vi: Vi, title=Forcemaster, class=forcemaster, store=cosmetics, traits=[blunt, loyal, protective, street-smart, angry, wounded], tags=[], speech=short punchy street fighter, dry sarcasm, protective edge, practical combat advice
- zagreus: Zagreus, title=Berserker, class=berserker, store=cosmetics, traits=[rebellious, charming, stubborn, compassionate, battle-hardened, death-defying], tags=[], speech=warm sarcastic underworld prince, quick jokes under pressure, sincere loyalty, direct combat advice
- zelda: Zelda, title=Light Dragon Restored, class=wizard, store=, traits=[patient, ancient-souled, gentle, scholarly, serene, melancholy-beneath-warmth], tags=[wizard_mentor, light_magic, skyland_lore, totk_reminiscence, dragon_memory, calm_demeanor], speech=quietly lyrical, sky and light metaphors, thoughtful pauses, warm scholarly counsel
- zuko: Zuko, title=Fire Wizard, class=fire_wizard, store=cosmetics, traits=[serious, redeeming, firebender, honorable, awkward, disciplined], tags=[], speech=short serious firebender, direct correction, breath and control metaphors, sincere but guarded

Output exactly 22 exchanges for these pairs:
001. link -> zagreus
002. link -> zelda
003. link -> zuko
004. marceline -> pope_leo
005. marceline -> shoumai
006. marceline -> tarnished
007. marceline -> toph
008. marceline -> traxex
009. marceline -> venti
010. marceline -> vi
011. marceline -> zagreus
012. marceline -> zelda
013. marceline -> zuko
014. pope_leo -> zelda
015. shoumai -> zelda
016. tarnished -> zelda
017. toph -> zelda
018. traxex -> zelda
019. venti -> zelda
020. vi -> zelda
021. zagreus -> zelda
022. zelda -> zuko
