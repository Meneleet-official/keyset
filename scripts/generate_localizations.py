"""Build offline locale files from the installed Minecraft catalog.

Only public UI strings are sent to the translation service. Profiles and user settings
are never read. Existing reviewed English/Russian files are the source of truth.
"""
import argparse
import concurrent.futures
import hashlib
import json
import re
import time
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LANG = ROOT / 'common/src/client/resources/assets/keyset/lang'
CACHE = ROOT / 'build/localization-cache'
FORMAT = re.compile(r'%(?:[1-9]\d*\$)?s|%%')
FAMILIES = {
    'ast_es':'es','bar':'de','brb':'nl','de_at':'de','de_ch':'de',
    'esan':'es','fra_de':'de','fur_it':'it','go_fr':'fr','hal_ua':'uk',
    'hn_no':'nn','ksh':'de','nds_de':'de','ovd':'sv','qcb_es':'es','ry_ua':'uk',
    'sxu':'de','vro':'et','val_es':'ca','vec_it':'it','nn_no':'nn','no_no':'no',
    'lzh':'zh-TW','zlm_arab':'ms','en_pt':'en','en_ud':'en','enp':'en','enws':'en',
    'lol_us':'en','rpr':'ru','qid':'id','zh_cn':'zh-CN','zh_tw':'zh-TW','zh_hk':'zh-TW',
}
UNSUPPORTED = {'got_de','io_en','isv','jbo_en','kw_gb','pls','qya_aa','tlh_aa','tok','vp_vl','tzo_mx'}
VANILLA_KEYS = {
    'keyset.done':'gui.done','keyset.cancel':'gui.cancel','keyset.confirm':'gui.yes',
    'keyset.reset':'controls.reset','keyset.reset_all':'controls.resetAll',
    'keyset.compare_back':'gui.back','keyset.compare_unbound':'key.keyboard.unknown',
    'keyset.input_mouse':'controls.mouse','keyset.mouse_left':'key.mouse.left',
    'keyset.mouse_middle':'key.mouse.middle','keyset.mouse_right':'key.mouse.right',
}


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def placeholders(value):
    return sorted(FORMAT.findall(value))


def translate_line(value, language):
    tokens = []
    def mask(match):
        tokens.append(match[0])
        return str(987654320 + len(tokens) - 1)
    protected = FORMAT.sub(mask, value)
    url = 'https://translate.googleapis.com/translate_a/single?' + urllib.parse.urlencode({
        'client':'gtx','sl':'en','tl':language,'dt':'t','q':protected})
    with urllib.request.urlopen(url, timeout=35) as response:
        data = json.load(response)
    result = ''.join(part[0] for part in data[0] if part[0])
    for i, token in enumerate(tokens):
        digits = str(987654320 + i)
        result = re.sub(r'[\s,.]*'.join(digits), lambda _: token, result)
    if not result.strip():
        raise ValueError('Translation service returned an empty string')
    if placeholders(result) != placeholders(value):
        raise ValueError('Changed protected formatting parameters')
    return result


def translate(values, language):
    if language == 'en':
        return dict(values)
    result = {}
    batches, current, length = [], [], 0
    for index, (key, value) in enumerate(values.items()):
        if not re.search(r'[A-Za-z]', FORMAT.sub('', value)):
            result[key] = value
            continue
        line = f'{index:04d}||{value}'
        if length + len(line) > 4300 and current:
            batches.append(current)
            current, length = [], 0
        current.append((index, key, value, line))
        length += len(line) + 1
    if current:
        batches.append(current)
    for batch in batches:
        query = '\n'.join(row[3] for row in batch)
        url = 'https://translate.googleapis.com/translate_a/single?' + urllib.parse.urlencode({
            'client':'gtx','sl':'en','tl':language,'dt':'t','q':query})
        failure = None
        for attempt in range(3):
            try:
                request = urllib.request.Request(url, headers={'User-Agent':'Keyset-Localization/1.0'})
                with urllib.request.urlopen(request, timeout=35) as response:
                    data = json.load(response)
                text = ''.join(part[0] for part in data[0] if part[0])
                translated = {}
                matches = list(re.finditer(r'(?<!\d)(\d{1,4})\s*\|(?:\s*\|)?\s*', text))
                for i, match in enumerate(matches):
                    end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
                    translated[int(match[1])] = text[match.end():end].strip()
                for index, key, original, _ in batch:
                    value = translated.get(index, '')
                    value = re.sub(r'%\s*(?:(\d+)\s*\$\s*)?[sS]',
                        lambda m: '%' + (m[1] + '$' if m[1] else '') + 's', value)
                    if not value or placeholders(value) != placeholders(original):
                        value = translate_line(original, language)
                    result[key] = value
                failure = None
                break
            except Exception as error:
                failure = error
                time.sleep(1 + attempt)
        if failure:
            raise RuntimeError(f'{language}: {failure}')
    return result


def transform_variant(locale, values):
    replacements = {
        'en_pt': {'profile':'chart','profiles':'charts','Choose':'Pick','Select':'Pick','Cancel':'Belay',
                  'bindings':'key orders','binding':'key order','Saved':'Stowed','Back':'Aback','Delete':'Scuttle'},
        'enp': {'profile':'keyset','profiles':'keysets','actions':'deeds','action':'deed','conflicts':'clashes',
                'conflict':'clash','Import':'Bring in','Export':'Send out','Cancel':'Belay','Reset':'Edset'},
        'enws': {'your':'thy','Your':'Thy','you':'thou','You':'Thou','actions':'deeds','action':'deed',
                 'Choose':'Choose thou','Press':'Strike','Delete':'Banish','Reset':'Set anew'},
        'lol_us': {'your':'ur','Your':'Ur','the':'teh','The':'Teh','profile':'purrfile','profiles':'purrfiles',
                   'Choose':'Pickz','Select':'Pickz','Saved':'Saevd','Cancel':'Nope','bindings':'key bindez'},
    }
    transformed = {}
    for key, original in values.items():
        value = original
        for word, target in replacements.get(locale, {}).items():
            value = re.sub(r'\b' + re.escape(word) + r'\b', target, value)
        if locale == 'qid':
            for source, target in [('sy','sj'),('ny','nj'),('kh','ch'),('j','dj'),('c','tj'),('y','j'),('u','oe')]:
                value = value.replace(source, target)
        if locale == 'rpr':
            value = re.sub(r'и(?=[аеёиоуыэюяй])', 'і', value, flags=re.IGNORECASE)
            value = re.sub(r'([бвгджзклмнпрстфхцчшщ])\b', r'\1ъ', value, flags=re.IGNORECASE)
        if locale == 'en_ud':
            protected = []
            def protect(match):
                protected.append(match[0])
                return chr(0xE000 + len(protected) - 1)
            value = FORMAT.sub(protect, value)
            value = value.translate(str.maketrans('abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ.,!?()[]',
                'ɐqɔpǝɟƃɥᴉɾʞlɯuodbɹsʇnʌʍxʎz∀𐐒ƆᗡƎℲ⅁HIſʞ⅂WNOԀΌᴚS⊥∩ΛMX⅄Z˙\u2018¡¿)(]['))[::-1]
            for i, token in enumerate(protected):
                value = value.replace(chr(0xE000 + i), token)
        if locale == 'sr_cs':
            # Minecraft distinguishes Serbian Latin (sr_cs) and Cyrillic (sr_sp).
            letters = dict(zip('абвгдђежзијклљмнњопрстћуфхцчџш',
                ['a','b','v','g','d','đ','e','ž','z','i','j','k','l','lj','m','n','nj','o','p','r','s','t','ć','u','f','h','c','č','dž','š']))
            value = ''.join(letters.get(c, letters.get(c.lower(), c).capitalize() if c.isupper() else c) for c in value)
        transformed[key] = value
    return transformed


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--assets', type=Path, required=True)
    parser.add_argument('--index', default='26.3-34')
    parser.add_argument('--workers', type=int, default=3)
    args = parser.parse_args()
    english = read(LANG / 'en_us.json')
    russian = read(LANG / 'ru_ru.json')
    assert english.keys() == russian.keys(), 'English/Russian key mismatch'
    index = read(args.assets / 'indexes' / (args.index + '.json'))['objects']
    catalogs, names = {}, {}
    for asset, entry in index.items():
        if asset.startswith('minecraft/lang/') and asset.endswith('.json'):
            locale = Path(asset).stem
            digest = entry['hash']
            catalogs[locale] = read(args.assets / 'objects' / digest[:2] / digest)
            names[locale] = {'name':catalogs[locale].get('language.name',locale),
                             'region':catalogs[locale].get('language.region','')}
    targets = {locale: FAMILIES.get(locale, locale.split('_')[0]) for locale in catalogs}
    targets['nn_no'] = targets['hn_no'] = 'no'
    targets['nah'] = 'nhe'
    for locale in UNSUPPORTED:
        if locale in targets:
            targets[locale] = 'en'
    source_hash = hashlib.sha256(json.dumps(english, sort_keys=True).encode()).hexdigest()
    languages = sorted(set(targets.values()) - {'en','ru'})
    translated = {'en':english,'ru':russian}
    failures = {}

    def one(language):
        cache = CACHE / (language + '.json')
        old = read(cache) if cache.exists() else {'source':{},'values':{}}
        values = {key:old['values'][key] for key in english if key in old['values']
                  and old['values'][key].strip() and old['source'].get(key)==english[key]}
        missing = {key:value for key,value in english.items() if key not in values}
        if missing:
            values.update(translate(missing, language))
            write(cache, {'source':english,'values':values})
        return language, values

    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        tasks = {pool.submit(one, language):language for language in languages}
        for task in concurrent.futures.as_completed(tasks):
            language = tasks[task]
            try:
                language, values = task.result()
                translated[language] = values
                print('translated',language,flush=True)
            except Exception as error:
                failures[language] = str(error)
                print('unavailable',language,str(error),flush=True)

    manifest = {'minecraft':'26.3','release':'1.0.0','sourceHash':source_hash,'locales':{},'unavailableProviders':failures}
    for locale in sorted(catalogs):
        if locale == 'ru_ru':
            values, mode = russian, 'reviewed'
        else:
            language = targets[locale]
            values = translated.get(language, english)
            mode = 'machine' if language in translated and language not in {'en','ru'} else 'regional'
            if mode == 'machine' and locale in FAMILIES:
                mode = 'machine-regional'
            if locale in UNSUPPORTED or language in failures:
                mode = 'vanilla-with-english-fallback'
            values = transform_variant(locale, values)
            native = catalogs[locale]
            for mod_key, native_key in VANILLA_KEYS.items():
                if mod_key in english and native_key in native:
                    values[mod_key] = native[native_key]
            # Product names are labels, not words for machine translation.
            for key in ('keyset.title', 'keyset.header_short', 'keyset.open', 'key.category.keyset.general'):
                values[key] = english[key]
            for key, original in english.items():
                if placeholders(values[key]) != placeholders(original):
                    values[key] = original
            write(LANG / (locale + '.json'), {key:values[key] for key in english})
        manifest['locales'][locale] = dict(names[locale], providerLanguage=targets[locale], quality=mode)
    manifest['locales']['en_us'] = {'name':'English','region':'United States','quality':'reviewed'}
    write(ROOT / 'common/src/client/resources/assets/keyset/localization_manifest.json', manifest)
    write(ROOT / 'scripts/minecraft_locales_26_3.json', names)
    print('locale files',len(manifest['locales']),'provider failures',len(failures),flush=True)


if __name__ == '__main__':
    main()
