# ReActAgent 结构化输出（Structured Output）详解

## 目录

1. [什么是结构化输出](#什么是结构化输出)
2. [快速上手](#快速上手)
3. [完整使用示例](#完整使用示例)
4. [核心原理](#核心原理)
5. [两种提醒模式对比](#两种提醒模式对比)
6. [动态 Schema](#动态-schema)
7. [流式结构化输出](#流式结构化输出)
8. [Schema 定义规范](#schema-定义规范)
9. [注意事项与最佳实践](#注意事项与最佳实践)

---

## 什么是结构化输出

结构化输出是指让大语言模型（LLM）**按照预定义的 Schema（数据结构）返回结果**，而不是返回自由格式的文本。

**为什么需要结构化输出？**

| 场景 | 纯文本输出 | 结构化输出 |
|------|-----------|-----------|
| 提取联系人信息 | `"张三，电话 138-xxxx，邮箱 zhang@example.com"` | `ContactInfo { name="张三", phone="138-xxxx", email="zhang@example.com" }` |
| 情感分析 | `"这条评论总体是正面的"` | `SentimentAnalysis { overallSentiment="positive", positiveScore=0.85 }` |
| 下游程序处理 | 需要写正则/ NLP 解析 | 直接 `obj.getName()` 取用 |

AgentScope 的 `ReActAgent` 内置了完整的结构化输出支持，无需手写 Prompt 约束格式，只需定义一个 Java 类即可。

---

## 快速上手

### 第一步：定义 Schema

创建一个普通的 Java 类，字段必须是 **public**，且必须有 **无参构造器**。

```java
public class ProductRequirements {
    public String productType;     // 产品类型
    public String brand;           // 品牌偏好
    public Integer minRam;         // 最小内存
    public Double maxBudget;       // 最高预算
    public List<String> features;  // 其他需求

    public ProductRequirements() {}  // 必须有无参构造器
}
```

### 第二步：请求结构化输出

```java
// 构建用户消息
Msg userMsg = Msg.builder()
    .role(MsgRole.USER)
    .content(TextBlock.builder()
        .text("我想买一台笔记本电脑，至少16G内存，预算2000美元左右")
        .build())
    .build();

// 发起调用，传入 Schema Class
Msg response = agent.call(userMsg, ProductRequirements.class).block();

// 提取结构化数据
ProductRequirements result = response.getStructuredData(ProductRequirements.class);

System.out.println("产品类型: " + result.productType);  // "laptop"
System.out.println("品牌偏好: " + result.brand);        // 可能为 null
System.out.println("最小内存: " + result.minRam);       // 16
System.out.println("最高预算: " + result.maxBudget);    // 2000.0
```

### 第三步：创建支持结构化输出的 Agent

```java
ReActAgent agent = ReActAgent.builder()
    .name("Analyzer")
    .sysPrompt("你是一个智能分析助手，分析用户需求并返回结构化数据。")
    .model(DashScopeChatModel.builder()
        .apiKey(apiKey)
        .modelName("qwen-max")
        .build())
    .toolkit(new Toolkit())
    .memory(new InMemoryMemory())
    .structuredOutputReminder(StructuredOutputReminder.TOOL_CHOICE)  // 默认
    .build();
```

---

## 完整使用示例

```java
public class StructuredOutputDemo {

    public static void main(String[] args) {
        // 1. 创建 Agent
        ReActAgent agent = ReActAgent.builder()
            .name("ContactExtractor")
            .sysPrompt("从文本中提取联系人信息")
            .model(DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-max")
                .build())
            .toolkit(new Toolkit())
            .memory(new InMemoryMemory())
            .build();

        // 2. 定义 Schema
        // public class ContactInfo {
        //     public String name;
        //     public String email;
        //     public String phone;
        //     public String company;
        //     public ContactInfo() {}
        // }

        // 3. 准备输入
        String text = "请联系 John Smith，邮箱 john.smith@example.com，" +
                      "电话 +1-555-123-4567，公司 TechCorp Inc.";

        Msg userMsg = Msg.builder()
            .role(MsgRole.USER)
            .content(TextBlock.builder()
                .text("提取以下文本的联系人信息：" + text)
                .build())
            .build();

        // 4. 调用并提取结果
        try {
            Msg response = agent.call(userMsg, ContactInfo.class).block();
            ContactInfo info = response.getStructuredData(ContactInfo.class);

            System.out.println("姓名: " + info.name);
            System.out.println("邮箱: " + info.email);
            System.out.println("电话: " + info.phone);
            System.out.println("公司: " + info.company);
        } catch (Exception e) {
            System.err.println("提取失败: " + e.getMessage());
        }
    }
}
```

---

## 核心原理

### 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                      ReActAgent                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │           StructuredOutputCapableAgent                 │  │
│  │  ┌─────────────────────────────────────────────────┐  │  │
│  │  │  1. 将 Class → JSON Schema                        │  │  │
│  │  │     (JsonSchemaUtils.generateSchemaFromClass)    │  │  │
│  │  │                                                  │  │  │
│  │  │  2. 注册临时工具 "generate_response"              │  │  │
│  │  │     参数 = Schema 定义                           │  │  │
│  │  │                                                  │  │  │
│  │  │  3. 挂载 StructuredOutputHook 控制流程           │  │  │
│  │  └─────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────┘  │
│                           │                                  │
│                           ▼                                  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              ReAct 循环 (Reasoning + Acting)           │  │
│  │  ┌─────────────┐    ┌──────────────┐    ┌──────────┐  │  │
│  │  │  Reasoning  │───→│  Acting      │───→│  下一轮   │  │  │
│  │  │  (模型推理)  │    │  (工具执行)   │    │          │  │  │
│  │  └─────────────┘    └──────────────┘    └──────────┘  │  │
│  └───────────────────────────────────────────────────────┘  │
│                           │                                  │
│                           ▼                                  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              StructuredOutputHook                      │  │
│  │  ┌─────────────┐    ┌──────────────┐    ┌──────────┐  │  │
│  │  │ PreReasoning│    │ PostReasoning│    │PostActing│  │  │
│  │  │ 强制tool_   │    │ 检查是否调用 │    │完成时停止│  │  │
│  │  │  choice     │    │ generate_    │    │Agent循环 │  │  │
│  │  └─────────────┘    │ response     │    └──────────┘  │  │
│  │                     └──────────────┘                   │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 原理详解

#### 1. Schema → 临时工具转换

当你调用 `agent.call(msg, ProductRequirements.class)` 时，框架内部会：

```java
// 1. 将 Java Class 转为 JSON Schema
Map<String, Object> jsonSchema = JsonSchemaUtils.generateSchemaFromClass(ProductRequirements.class);
// 结果示例：
// {
//   "type": "object",
//   "properties": {
//     "productType": {"type": "string"},
//     "brand": {"type": "string"},
//     "minRam": {"type": "integer"},
//     "maxBudget": {"type": "number"},
//     "features": {"type": "array", "items": {"type": "string"}}
//   }
// }

// 2. 注册一个临时 AgentTool，名为 "generate_response"
AgentTool structuredOutputTool = new AgentTool() {
    @Override
    public String getName() { return "generate_response"; }

    @Override
    public String getDescription() {
        return "Generate the final structured response. Call this function when"
             + " you have all the information needed to provide a complete answer.";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
            "type", "object",
            "properties", Map.of("response", jsonSchema),
            "required", List.of("response")
        );
    }
    // ...
};
```

**关键点**：AgentScope 不是让模型直接输出 JSON 文本，而是让模型**以 Function Calling（工具调用）的方式输出**。这样：
- 模型会生成一个 `ToolUseBlock`，参数就是符合 Schema 的 JSON 对象
- 底层 LLM API（如 OpenAI、DashScope）会在协议层保证 JSON 格式合法
- 如果参数类型不匹配，模型会在生成阶段就被拒绝/重试

#### 2. StructuredOutputHook 的流程控制

`StructuredOutputHook` 是一个 Hook（钩子），挂载在 Agent 的 ReAct 循环上，通过事件驱动控制结构化输出的流程：

```
用户请求
    │
    ▼
┌─────────────┐
│ PreReasoning│  ← TOOL_CHOICE 模式下：如果是重试轮次，
│   Event     │     设置 tool_choice = "generate_response"（强制调用）
└─────────────┘
    │
    ▼
┌─────────────┐
│  Reasoning  │  ← 模型生成，可能调用工具（业务工具或 generate_response）
│  (模型推理)  │
└─────────────┘
    │
    ▼
┌─────────────┐
│PostReasoning│  ← 检查模型是否调用了 generate_response：
│   Event     │     - 如果调用了 → 继续 Acting
│             │     - 如果没调用 → 注入提醒消息，gotoReasoning 重试
└─────────────┘     （最多重试 3 次）
    │
    ▼
┌─────────────┐
│   Acting    │  ← 执行工具调用。如果是 generate_response：
│  (工具执行)  │     将 JSON 参数保存为结构化数据
└─────────────┘
    │
    ▼
┌─────────────┐
│ PostActing  │  ← 如果 generate_response 成功执行：
│   Event     │     设置 completed=true，停止 Agent 循环
└─────────────┘
    │
    ▼
┌─────────────┐
│  PostCall   │  ← Memory 压缩：删除所有中间消息（提醒消息、ToolUse、
│   Event     │     ToolResult），只保留最终结果消息
└─────────────┘
```

#### 3. 数据流转过程

```
模型输出 ToolUseBlock
    │
    │ 参数: {"response": {"productType": "laptop", "minRam": 16, ...}}
    ▼
ToolExecutor 执行 generate_response 工具
    │
    ▼
工具内部创建 Msg，metadata 中放入 responseData
    │
    ▼
StructuredOutputHook 检测到 generate_response 完成
    │
    ▼
停止 Agent 循环，提取 Msg 中的 responseData
    │
    ▼
存入最终 Msg 的 metadata，key = "agentscope_structured_output"
    │
    ▼
用户调用 msg.getStructuredData(ProductRequirements.class)
    │
    ▼
Jackson 反序列化为 Java 对象
```

#### 4. Memory 压缩（透明清理）

结构化输出过程中，Agent 的 Memory 会产生一些"中间消息"：
- 提醒消息（"请调用 generate_response"）
- 模型的 ToolUseBlock（调用 generate_response）
- 工具的 ToolResultBlock（执行结果）

`StructuredOutputHook` 在 `PostCallEvent` 阶段会自动清理这些中间消息，只保留：
- 用户的原始输入
- 最终的结构化输出结果（附带 `agentscope_structured_output` metadata）

这样对下游逻辑完全透明，就像 Agent 直接返回了一个结构化结果一样。

---

## 两种提醒模式对比

`ReActAgent.Builder` 通过 `structuredOutputReminder()` 方法配置模式：

```java
ReActAgent agent = ReActAgent.builder()
    .structuredOutputReminder(StructuredOutputReminder.TOOL_CHOICE)  // 默认
    // 或
    .structuredOutputReminder(StructuredOutputReminder.PROMPT)
    .build();
```

| 维度 | `TOOL_CHOICE`（默认） | `PROMPT` |
|------|----------------------|---------|
| **实现机制** | 利用 LLM API 的 `tool_choice` 参数强制调用指定工具 | 向对话中插入文本提醒消息 |
| **第一次调用** | 让模型自由决定（可以调业务工具或 `generate_response`） | 同左 |
| **模型未调用时** | 第二轮自动设置 `tool_choice = Specific("generate_response")` | 插入消息："Please call the 'generate_response' function to provide your response." 然后重试 |
| **重试效率** | 通常 1~2 轮即可 | 可能需要 2~4 轮 |
| **API 调用次数** | 少（利用协议层强制） | 多（纯对话引导） |
| **适用模型** | 支持 `tool_choice` 的模型（OpenAI、DashScope/qwen、Anthropic 等） | 兼容所有支持 Function Calling 的模型 |
| **是否推荐** | ✅ 强烈推荐 | ⚠️ 降级/兼容方案 |

### TOOL_CHOICE 的代码逻辑

```java
// StructuredOutputHook.handlePreReasoning()
if (reminderMode == StructuredOutputReminder.TOOL_CHOICE) {
    Msg lastMsg = inputMessages.get(inputMessages.size() - 1);
    if (lastMsg != null && isToolChoiceReminderMessage(lastMsg)) {
        // 强制设置 tool_choice 为 generate_response
        GenerateOptions options = GenerateOptions.builder()
            .toolChoice(new ToolChoice.Specific(TOOL_NAME))
            .build();
        event.setGenerateOptions(options);
    }
}
```

### PROMPT 的代码逻辑

```java
// StructuredOutputHook.createReminderMessage()
private Msg createReminderMessage(StructuredOutputReminder mode) {
    return Msg.builder()
        .name("system")
        .role(MsgRole.USER)
        .content(TextBlock.builder()
            .text("Please call the 'generate_response' function to provide your response.")
            .build())
        .metadata(Map.of(
            "agentscope_structured_output_reminder", true,
            "agentscope_structured_output_reminder_type", mode.toString()
        ))
        .build();
}
```

---

## 动态 Schema

除了传入 Java Class，你还可以传入 `JsonNode` 动态定义 Schema，适合 Schema 在运行时才能确定的场景。

```java
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// 从 JSON 字符串或外部配置加载 Schema
String schemaJson = """
    {
        "type": "object",
        "properties": {
            "productName": {"type": "string"},
            "price": {"type": "number"},
            "tags": {
                "type": "array",
                "items": {"type": "string"}
            }
        }
    }
    """;

JsonNode schema = new ObjectMapper().readTree(schemaJson);

// 使用动态 Schema 调用
Msg response = agent.call(userMsg, schema).block();

// 提取为 Map（因为不知道具体类型）
Map<String, Object> data = response.getStructuredData(false);
System.out.println(data.get("productName"));
System.out.println(data.get("price"));
```

> 注意：`response.getStructuredData(false)` 返回的是 Map，不可变副本；`response.getStructuredData(true)` 返回可变副本。

---

## 流式结构化输出

结构化输出也支持流式 API，适合需要实时展示进度的场景（如前端展示 Agent 思考过程）。

```java
import reactor.core.publisher.Flux;

// 发起流式结构化输出请求
Flux<Event> eventFlux = agent.stream(
    userMsg,
    StreamOptions.defaults(),
    ProductRequirements.class
);

// 订阅流事件（可以在这里展示思考过程、工具调用等）
eventFlux.subscribe(event -> {
    if (event.getEventType() == Event.EventType.REASONING_CHUNK) {
        System.out.println("思考中: " + event.getMessage().getTextContent());
    }
});

// 获取最终结果（阻塞等待）
ProductRequirements result = eventFlux
    .blockLast()
    .getMessage()
    .getStructuredData(ProductRequirements.class);
```

> 流式场景下，最终的结构化数据仍然要等到 `generate_response` 工具调用完成后才能获取完整结果。

---

## Schema 定义规范

### 基本要求

```java
public class MySchema {
    // 1. 字段必须是 public
    public String name;
    public Integer age;

    // 2. 必须有无参构造器
    public MySchema() {}
}
```

### 支持的类型

| Java 类型 | JSON Schema 类型 | 示例 |
|-----------|-----------------|------|
| `String` | `string` | `public String title;` |
| `Integer` / `int` | `integer` | `public Integer count;` |
| `Double` / `double` | `number` | `public Double score;` |
| `Boolean` / `boolean` | `boolean` | `public Boolean active;` |
| `List<T>` | `array` | `public List<String> tags;` |
| `Map<String, Object>` | `object` (additionalProperties) | `public Map<String, Object> meta;` |
| 嵌套对象 | `object` | `public Address address;` |

### 嵌套结构示例

```java
public class Order {
    public String orderId;
    public Customer customer;
    public List<OrderItem> items;
    public Double totalAmount;

    public Order() {}
}

public class Customer {
    public String name;
    public String email;

    public Customer() {}
}

public class OrderItem {
    public String productName;
    public Integer quantity;
    public Double unitPrice;

    public OrderItem() {}
}
```

### Jackson 注解支持

```java
public class CustomSchema {
    // 自定义 JSON 字段名
    @JsonProperty("product_name")
    public String productName;

    // 忽略该字段
    @JsonIgnore
    public String internalId;

    // 字段描述（会写入 Schema description，帮助模型理解）
    @JsonPropertyDescription("产品的市场价格，单位为美元")
    public Double price;

    public CustomSchema() {}
}
```

---

## 注意事项与最佳实践

### 1. 与业务工具共存

结构化输出不会阻碍 Agent 使用其他业务工具。模型可以：
1. 先调用多个业务工具收集信息
2. 最后调用 `generate_response` 输出结构化结果

这是 `TOOL_CHOICE` 模式的优势——第一次调用不强制 `generate_response`，给模型自由思考的空间。

```java
// Agent 可能的行为序列：
// 1. Reasoning: "我需要先查一下数据库"
// 2. Acting: 调用 queryDatabase 工具
// 3. Reasoning: "现在我有了足够信息，输出结果"
// 4. Acting: 调用 generate_response 工具（结构化输出）
// 5. StructuredOutputHook: 停止循环，返回结果
```

### 2. 错误处理

```java
try {
    Msg response = agent.call(userMsg, ProductInfo.class).block();

    // 先检查是否有结构化数据
    if (!response.hasStructuredData()) {
        System.err.println("模型未返回结构化数据");
        return;
    }

    ProductInfo data = response.getStructuredData(ProductInfo.class);

    // 业务校验
    if (data.price == null || data.price < 0) {
        throw new IllegalArgumentException("价格数据无效");
    }

} catch (IllegalArgumentException e) {
    // Schema 不匹配或转换失败
    System.err.println("数据格式错误: " + e.getMessage());
} catch (Exception e) {
    // 其他错误（网络、模型异常等）
    System.err.println("调用失败: " + e.getMessage());
}
```

### 3. 线程安全

**Agent 实例不是线程安全的**。不要同时在多个线程中调用同一个 Agent 的 `call()` 或 `stream()` 方法。如果需要并发处理，请创建多个 Agent 实例。

### 4. 生命周期管理

每次结构化输出调用时，框架会：
- **动态注册** `generate_response` 工具
- **挂载** `StructuredOutputHook`

调用结束后（无论成功或失败），框架会自动：
- **卸载** Hook
- **移除** 临时工具

对用户完全透明，无需手动管理。

### 5. 性能考量

- **TOOL_CHOICE 模式**：通常只有 1 轮 Reasoning + 1 轮 Acting，开销最小
- **PROMPT 模式**：可能需要多轮重试，每次重试都是一次完整的 API 调用
- 如果模型总是忘记调用 `generate_response`，考虑在 System Prompt 中明确提醒：
  ```java
  .sysPrompt("完成任务后，必须调用 generate_response 工具输出最终结果。")
  ```

---

## 完整类图

```
┌──────────────────┐         ┌──────────────────────────────┐
│    AgentBase     │◄────────│ StructuredOutputCapableAgent │
│  (抽象基类)       │         │  (结构化输出基础设施)          │
└──────────────────┘         └──────────────┬───────────────┘
                                            │
                             ┌──────────────┘
                             ▼
                    ┌──────────────────┐
                    │    ReActAgent    │
                    │  (ReAct 循环实现) │
                    └──────────────────┘
                             │
        ┌────────────────────┼────────────────────┐
        ▼                    ▼                    ▼
┌───────────────┐   ┌─────────────────┐   ┌─────────────┐
│  doCall(msgs) │   │doCall(msgs,Class)│  │doCall(msgs, │
│   普通调用     │   │  Class Schema    │   │  JsonNode)  │
│               │   │                 │   │ 动态 Schema  │
└───────────────┘   └─────────────────┘   └─────────────┘
                             │
                             ▼
              ┌────────────────────────────┐
              │   executeWithStructuredOutput │
              │  1. Class → JSON Schema      │
              │  2. 注册 generate_response   │
              │  3. 创建 StructuredOutputHook│
              └────────────────────────────┘
                             │
                             ▼
              ┌────────────────────────────┐
              │     StructuredOutputHook     │
              │  PreReasoning: 强制 tool_choice│
              │  PostReasoning: 检查+重试     │
              │  PostActing: 完成时停止       │
              │  PostCall: Memory 压缩        │
              └────────────────────────────┘
```

---

## 参考

- **源码示例**: `agentscope-examples/quickstart/src/main/java/io/agentscope/examples/quickstart/StructuredOutputExample.java`
- **核心类**: `agentscope-core/src/main/java/io/agentscope/core/agent/StructuredOutputCapableAgent.java`
- **Hook 实现**: `agentscope-core/src/main/java/io/agentscope/core/agent/StructuredOutputHook.java`
- **提醒模式**: `agentscope-core/src/main/java/io/agentscope/core/model/StructuredOutputReminder.java`
