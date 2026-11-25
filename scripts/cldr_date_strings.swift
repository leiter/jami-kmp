// Month names, the full-date pattern, "Today"/"Yesterday"/"Now" and the short time units for
// every locale folder, taken from the CLDR data Foundation ships (macOS), as TSV for
// scripts/insert_translations.py:
//
//     swift scripts/cldr_date_strings.swift > /tmp/cldr.tsv
//     python3 scripts/insert_translations.py /tmp/cldr.tsv
//
// Keys: date_month_1…12 and date_full_format (shared/.../ui/utils/LocalizedTime.kt builds the
// chat date separator from them: %1$s month, %2$d day, %3$d year), date_today, date_yesterday,
// time_now_short, time_{minutes,hours,days,weeks}_short (conversation list), pref_days_short and
// pref_seconds_short (settings chips).
//
// A folder whose language Foundation has no data for is skipped and reported on stderr; those
// need a hand translation. Numbers are always ASCII %d: Compose formats them, not CLDR.
//
// Month names are the format-context (inflected) forms, because they are only used inside
// date_full_format. Where the locale writes the month as a number (ja, zh, ko, …) the "names"
// are the plain numbers and the pattern carries the 月/월 literal.

import Foundation

// Folder suffix -> ICU locale. Folders not listed use their suffix with -r -> _.
let overrides: [String: String] = [
    "zh": "zh_Hans", "zh-rCN": "zh_Hans_CN", "zh-rTW": "zh_Hant_TW", "zh-rHK": "zh_Hant_HK",
    "cmn": "zh_Hant", "hak": "zh_Hant", "nan": "zh_Hant",
    "sr": "sr_Latn", "az-rIR": "az_Arab", "fa-rIR": "fa_IR", "ast-rES": "ast_ES",
]

let resDir = URL(fileURLWithPath: CommandLine.arguments.count > 1
    ? CommandLine.arguments[1]
    : "shared/src/commonMain/composeResources")
let folders = try FileManager.default.contentsOfDirectory(atPath: resDir.path)
    .filter { $0.hasPrefix("values-") }
    .map { String($0.dropFirst("values-".count)) }
    .sorted()

let known = Set(Locale.availableIdentifiers)
var gregorian = Calendar(identifier: .gregorian)
gregorian.timeZone = TimeZone(identifier: "UTC")!

// Foundation falls back to English for a unit or word a locale lacks (ja weeks -> "5w", oc
// "Today"). Such a value is not a translation; it is dropped so the folder keeps the catalogue's
// English fallback. "h", "s", "m", "d" are left alone: many languages use those symbols too.
let englishFallbacks: Set<String> = ["%1$dw", "Today", "Yesterday", "Now", "now"]

func emit(_ folder: String, _ key: String, _ value: String) {
    if englishFallbacks.contains(value) {
        FileHandle.standardError.write("skip \(folder) \(key): English fallback \(value)\n".data(using: .utf8)!)
        return
    }
    print("\(folder)\t\(key)\t\(value)")
}

/// Turns an ICU date pattern into the catalogue's printf form, or nil if it has fields we
/// cannot express.
func printfPattern(_ icu: String) -> (pattern: String, numericMonth: Bool)? {
    var out = ""
    var numericMonth = false
    let chars = Array(icu)
    var i = 0
    while i < chars.count {
        let c = chars[i]
        if c == "'" {
            // quoted literal; '' is a single quote
            if i + 1 < chars.count && chars[i + 1] == "'" { out.append("'"); i += 2; continue }
            i += 1
            while i < chars.count {
                if chars[i] == "'" {
                    if i + 1 < chars.count && chars[i + 1] == "'" { out.append("'"); i += 2; continue }
                    i += 1
                    break
                }
                out.append(chars[i]); i += 1
            }
            continue
        }
        if c.isLetter && c.isASCII {
            var run = 0
            while i < chars.count && chars[i] == c { run += 1; i += 1 }
            switch c {
            case "M", "L":
                out += "%1$s"
                if run <= 2 { numericMonth = true }
            case "d": out += "%2$d"
            case "y": out += "%3$d"
            default: return nil
            }
            continue
        }
        if c == "%" { return nil }
        out.append(c); i += 1
    }
    return (out, numericMonth)
}

func digits(_ n: Int, _ locale: Locale) -> String {
    let f = NumberFormatter()
    f.locale = locale
    f.usesGroupingSeparator = false
    return f.string(from: NSNumber(value: n)) ?? "\(n)"
}

func unit(_ component: Calendar.Component, _ locale: Locale) -> String? {
    var cal = gregorian
    cal.locale = locale
    let f = DateComponentsFormatter()
    f.calendar = cal
    f.unitsStyle = .abbreviated
    var dc = DateComponents()
    let sample = 5
    switch component {
    case .second: f.allowedUnits = .second; dc.second = sample
    case .minute: f.allowedUnits = .minute; dc.minute = sample
    case .hour: f.allowedUnits = .hour; dc.hour = sample
    case .day: f.allowedUnits = .day; dc.day = sample
    case .weekOfMonth: f.allowedUnits = .weekOfMonth; dc.weekOfMonth = sample
    default: return nil
    }
    guard let s = f.string(from: dc) else { return nil }
    let d = digits(sample, locale)
    guard s.components(separatedBy: d).count == 2 else { return nil }
    return s.replacingOccurrences(of: d, with: "%1$d")
}

for folder in folders where !folder.hasPrefix("de") {  // German is hand-curated
    let id = overrides[folder] ?? folder.replacingOccurrences(of: "-r", with: "_")
    let lang = String(id.split(separator: "_").first!)
    guard known.contains(id) || known.contains(lang) else {
        FileHandle.standardError.write("skip \(folder): no CLDR data for \(id)\n".data(using: .utf8)!)
        continue
    }
    let locale = Locale(identifier: id)
    var cal = gregorian
    cal.locale = locale

    // Full date
    if let icu = DateFormatter.dateFormat(fromTemplate: "MMMMdy", options: 0, locale: locale),
       let (pattern, numericMonth) = printfPattern(icu) {
        emit(folder, "date_full_format", pattern)
        let df = DateFormatter()
        df.locale = locale
        df.calendar = cal
        let names = numericMonth ? (1...12).map { "\($0)" } : df.monthSymbols!
        for (n, name) in names.enumerated() { emit(folder, "date_month_\(n + 1)", name) }
    } else {
        FileHandle.standardError.write("skip \(folder) date pattern\n".data(using: .utf8)!)
    }

    // Relative day words, sentence case
    let rel = RelativeDateTimeFormatter()
    rel.locale = locale
    rel.calendar = cal
    rel.dateTimeStyle = .named
    rel.unitsStyle = .full
    rel.formattingContext = .beginningOfSentence
    let today = rel.localizedString(from: DateComponents(day: 0))
    let yesterday = rel.localizedString(from: DateComponents(day: -1))
    let now = rel.localizedString(from: DateComponents(second: 0))
    // A named form has no digits; a numeric fallback ("in 0 days") is not a word for it.
    func named(_ s: String) -> Bool { s.rangeOfCharacter(from: .decimalDigits) == nil }
    if named(today) { emit(folder, "date_today", today) }
    if named(yesterday) { emit(folder, "date_yesterday", yesterday) }
    if named(now) { emit(folder, "time_now_short", now) }

    // Short units
    for (key, comp) in [("time_minutes_short", Calendar.Component.minute),
                        ("time_hours_short", .hour), ("time_days_short", .day),
                        ("time_weeks_short", .weekOfMonth), ("pref_days_short", .day),
                        ("pref_seconds_short", .second)] {
        if let s = unit(comp, locale) { emit(folder, key, s) }
    }
}
